package com.masterchefcuts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.WebhookEvent;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.WebhookEventRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    private final ListingRepository listingRepository;
    private final CutRepository cutRepository;
    private final OrderRepository orderRepository;
    private final ParticipantRepo participantRepo;
    private final ClaimRepository claimRepository;
    private final NotificationService notificationService;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Transactional
    public PaymentIntentResponse createCartIntent(String buyerId, List<Long> cutIds, String paymentType) throws StripeException {
        if (cutIds == null || cutIds.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        List<Long> distinctCutIds = cutIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctCutIds.size() != cutIds.size()) {
            throw new IllegalArgumentException("Cart contains duplicate or invalid cuts");
        }

        List<Cut> cuts = cutRepository.findByIdInWithListing(distinctCutIds);
        if (cuts.size() != distinctCutIds.size()) {
            throw new IllegalArgumentException("One or more cart cuts are invalid");
        }

        // IDEMPOTENCY CHECK: Prevent duplicate orders for the same cuts
        // Check if there's already a pending/paid order containing any of these cuts
        for (Long cutId : distinctCutIds) {
            String cutIdPattern = "\"cutId\":" + cutId;
            if (orderRepository.existsPendingOrderWithCut(buyerId, cutIdPattern)) {
                log.warn("Duplicate order attempt for cut {} by buyer {}", cutId, buyerId);
                throw new IllegalArgumentException("You already have a pending or completed order for one of these cuts. Please check your orders.");
            }
        }

        long amountCents = 0L;
        Map<Long, Long> totalCutsByListing = new HashMap<>();
        List<Map<String, Object>> orderItems = new ArrayList<>();

        for (Cut cut : cuts) {
            if (!cut.isClaimed() || cut.getClaimedBy() == null || !buyerId.equals(cut.getClaimedBy().getId())) {
                throw new IllegalArgumentException("Cart contains cuts not claimed by the current user");
            }

            Listing listing = cut.getListing();
            long totalCuts = totalCutsByListing.computeIfAbsent(listing.getId(), cutRepository::countByListingId);
            if (totalCuts <= 0) {
                throw new IllegalArgumentException("Listing has no cuts");
            }

            long cutAmountCents = Math.round((listing.getPricePerLb() * listing.getWeightLbs() / totalCuts) * 100);
            amountCents += cutAmountCents;

            Map<String, Object> item = new HashMap<>();
            item.put("cutId", cut.getId());
            item.put("cutLabel", cut.getLabel());
            item.put("listingId", listing.getId());
            item.put("animalType", String.valueOf(listing.getAnimalType()));
            item.put("breed", listing.getBreed());
            item.put("amountCents", cutAmountCents);
            orderItems.add(item);
        }

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Cart total must be greater than zero");
        }

        boolean isDeposit = "DEPOSIT".equalsIgnoreCase(paymentType);
        long chargeAmount = isDeposit ? (amountCents + 1) / 2 : amountCents; // ceiling half for deposit

        // Generate idempotency key based on buyer, cuts, and payment type
        // This ensures the same request won't create duplicate PaymentIntents
        String idempotencyKey = generateIdempotencyKey(buyerId, distinctCutIds, paymentType);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(chargeAmount)
                .setCurrency("usd")
                .putMetadata("type", "cart")
                .putMetadata("buyerId", buyerId)
                .putMetadata("paymentType", isDeposit ? "DEPOSIT" : "FULL")
                .putMetadata("cutCount", String.valueOf(distinctCutIds.size()))
                .putMetadata("cutIds", distinctCutIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""))
                .putMetadata("idempotencyKey", idempotencyKey)
                .build();
        
        // Use Stripe idempotency key to prevent duplicate PaymentIntents on network retries
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        PaymentIntent intent = PaymentIntent.create(params, requestOptions);

        Order order = new Order();
        order.setParticipantId(buyerId);
        order.setStripePaymentIntentId(intent.getId());
        order.setOrderDate(LocalDateTime.now().toString());
        order.setStatus("PENDING_PAYMENT");
        order.setAmountCents(amountCents);
        order.setCurrency("usd");
        order.setTotalAmount(amountCents / 100.0);
        order.setItems(toJson(orderItems));
        order.setPaymentType(isDeposit ? "DEPOSIT" : "FULL");
        if (isDeposit) {
            order.setRemainingAmountCents(amountCents - chargeAmount);
        }
        order.setNotes("Awaiting payment confirmation from Stripe webhook");
        orderRepository.save(order);

        return new PaymentIntentResponse(intent.getClientSecret(), chargeAmount, "usd");
    }

    @Transactional
    public void handleWebhook(String payload, String stripeSignatureHeader) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, stripeSignatureHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }

        // IDEMPOTENCY CHECK: Skip if we've already processed this event
        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Skipping duplicate webhook event: {}", event.getId());
            return;
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent intent)) {
            return;
        }

        // Record that we're processing this event (with unique constraint protection)
        try {
            WebhookEvent webhookEvent = WebhookEvent.builder()
                    .stripeEventId(event.getId())
                    .eventType(event.getType())
                    .paymentIntentId(intent.getId())
                    .build();
            webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            // Another thread already recorded this event - skip processing
            log.info("Webhook event {} already being processed by another thread", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> markOrderPaid(intent, event.getId());
            case "payment_intent.payment_failed", "payment_intent.canceled" -> markOrderFailed(intent, event.getId());
            default -> {
                // ignore unsupported payment events
            }
        }
    }

    private void markOrderPaid(PaymentIntent intent, String eventId) {
        // Use pessimistic locking to prevent race conditions
        Optional<Order> orderOpt = orderRepository.findByStripePaymentIntentIdForUpdate(intent.getId());
        if (orderOpt.isEmpty()) {
            orderOpt = orderRepository.findByBalancePaymentIntentIdForUpdate(intent.getId());
        }
        
        Order order = orderOpt.orElseGet(() -> createFallbackOrder(intent));

        // Idempotency check: skip if already paid
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} already marked as PAID, skipping duplicate webhook {}", order.getId(), eventId);
            return;
        }

        long confirmedAmount = intent.getAmountReceived() != null && intent.getAmountReceived() > 0
                ? intent.getAmountReceived()
                : intent.getAmount();

        // Determine if this is a balance payment (second half of deposit)
        boolean isBalancePayment = order.getBalancePaymentIntentId() != null
                && order.getBalancePaymentIntentId().equals(intent.getId());

        if (isBalancePayment) {
            // Balance payment completed — mark fully paid
            order.setRemainingAmountCents(0L);
            order.setStatus("PAID");
            order.setPaidAt(LocalDateTime.now().toString());
            order.setNotes(appendNote(order.getNotes(), "Balance payment confirmed via Stripe"));
        } else if ("DEPOSIT".equalsIgnoreCase(order.getPaymentType())) {
            // First deposit payment completed
            order.setStatus("DEPOSIT_PAID");
            order.setPaidAt(LocalDateTime.now().toString());
            order.setNotes(appendNote(order.getNotes(), "Deposit confirmed — awaiting balance"));
        } else {
            // Full payment
            if (confirmedAmount > 0) {
                order.setAmountCents(confirmedAmount);
                order.setTotalAmount(confirmedAmount / 100.0);
            }
            if (intent.getCurrency() != null) {
                order.setCurrency(intent.getCurrency());
            }
            order.setStatus("PAID");
            order.setPaidAt(LocalDateTime.now().toString());
            order.setNotes(appendNote(order.getNotes(), "Stripe confirmed payment_intent.succeeded"));
        }

        orderRepository.save(order);

        // Mark associated claims as paid so they won't be expired
        markClaimsPaidForOrder(order);

        // Notify farmers of the new paid order
        notifyFarmersOfPayment(order);

        if ("PAID".equalsIgnoreCase(order.getStatus())
                && order.getParticipantId() != null && !order.getParticipantId().isBlank()) {
            participantRepo.findById(order.getParticipantId()).ifPresent(p -> {
                p.setTotalSpent(p.getTotalSpent() + order.getTotalAmount());
                participantRepo.save(p);
            });
        }
    }

    private void notifyFarmersOfPayment(Order order) {
        try {
            String itemsJson = order.getItems();
            if (itemsJson == null || itemsJson.isBlank()) return;

            List<Map<String, Object>> items = objectMapper.readValue(itemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            // Get unique listing IDs and notify each farmer
            items.stream()
                    .map(i -> i.get("listingId"))
                    .filter(Objects::nonNull)
                    .map(id -> Long.parseLong(String.valueOf(id)))
                    .distinct()
                    .forEach(listingId -> {
                        listingRepository.findById(listingId).ifPresent(listing -> {
                            com.masterchefcuts.model.Participant farmer = listing.getFarmer();
                            if (farmer != null) {
                                String amount = String.format("$%.2f", order.getTotalAmount());
                                notificationService.send(farmer, 
                                        com.masterchefcuts.enums.NotificationType.ORDER_PAID,
                                        "💵", 
                                        "New Paid Order!",
                                        "You have a new order for " + amount + ". Accept it to begin processing.",
                                        listingId,
                                        order.getId());
                            }
                        });
                    });
        } catch (Exception e) {
            // Don't fail the payment flow if notification fails
        }
    }

    private void markOrderFailed(PaymentIntent intent, String eventId) {
        // Use pessimistic locking
        Optional<Order> orderOpt = orderRepository.findByStripePaymentIntentIdForUpdate(intent.getId());
        if (orderOpt.isEmpty()) {
            orderOpt = orderRepository.findByBalancePaymentIntentIdForUpdate(intent.getId());
        }
        
        orderOpt.ifPresent(order -> {
            // Skip if already paid or already failed
            if ("PAID".equalsIgnoreCase(order.getStatus()) || "PAYMENT_FAILED".equalsIgnoreCase(order.getStatus())) {
                log.info("Order {} already in terminal state {}, skipping webhook {}", order.getId(), order.getStatus(), eventId);
                return;
            }
            order.setStatus("PAYMENT_FAILED");
            String reason = intent.getLastPaymentError() != null
                    ? intent.getLastPaymentError().getMessage()
                    : "Stripe marked payment as failed";
            order.setNotes(appendNote(order.getNotes(), reason));
            orderRepository.save(order);
        });
    }

    private void markClaimsPaidForOrder(Order order) {
        try {
            String itemsJson = order.getItems();
            if (itemsJson == null || itemsJson.isBlank()) return;

            List<Map<String, Object>> items = objectMapper.readValue(itemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            List<Long> cutIds = items.stream()
                    .map(i -> i.get("cutId"))
                    .filter(Objects::nonNull)
                    .map(v -> ((Number) v).longValue())
                    .toList();

            if (!cutIds.isEmpty()) {
                List<com.masterchefcuts.model.Claim> claims = claimRepository.findByCutIdIn(cutIds);
                for (com.masterchefcuts.model.Claim claim : claims) {
                    claim.setPaid(true);
                }
                claimRepository.saveAll(claims);
            }
        } catch (Exception e) {
            // Don't fail the payment flow if claim marking fails
        }
    }

    /**
     * Generate a deterministic idempotency key for a payment request.
     * Same inputs will always produce the same key, preventing duplicate charges.
     */
    private String generateIdempotencyKey(String buyerId, List<Long> cutIds, String paymentType) {
        // Sort cut IDs to ensure consistent ordering
        List<Long> sortedCutIds = cutIds.stream().sorted().toList();
        String cutString = sortedCutIds.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        
        // Create a hash of the key components
        String keyInput = buyerId + "|" + cutString + "|" + paymentType + "|" + 
                          LocalDateTime.now().toLocalDate(); // Include date to allow retry next day if needed
        
        // Use SHA-256 hash for a clean idempotency key
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(keyInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return "cart_" + sb.substring(0, 32); // First 32 chars of hash
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback to simple string if hashing fails
            return "cart_" + buyerId + "_" + sortedCutIds.hashCode() + "_" + paymentType;
        }
    }

    private Order createFallbackOrder(PaymentIntent intent) {
        Order order = new Order();
        Map<String, String> metadata = intent.getMetadata();

        order.setParticipantId(metadata != null ? metadata.get("buyerId") : null);
        order.setStripePaymentIntentId(intent.getId());
        order.setOrderDate(LocalDateTime.now().toString());
        order.setStatus("PENDING_PAYMENT");
        order.setAmountCents(intent.getAmount());
        order.setTotalAmount(intent.getAmount() / 100.0);
        order.setCurrency(intent.getCurrency() != null ? intent.getCurrency() : "usd");
        order.setItems(metadata != null && metadata.get("cutIds") != null ? metadata.get("cutIds") : "[]");
        order.setNotes("Order reconstructed from Stripe webhook metadata");

        return orderRepository.save(order);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order items", e);
        }
    }

    private String appendNote(String existingNotes, String note) {
        if (existingNotes == null || existingNotes.isBlank()) {
            return note;
        }
        return existingNotes + " | " + note;
    }

    @Transactional
    public PaymentIntentResponse createIntent(PaymentIntentRequest request) throws StripeException {
        Listing listing = listingRepository.findByIdWithCuts(request.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        int totalCuts = listing.getCuts().size();
        if (totalCuts == 0) throw new IllegalArgumentException("Listing has no cuts");

        long amountCents = Math.round((listing.getPricePerLb() * listing.getWeightLbs() / totalCuts) * 100);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .putMetadata("listingId", String.valueOf(listing.getId()))
                .putMetadata("cutLabel", request.getCutLabel())
                .build();

        PaymentIntent intent = PaymentIntent.create(params);

        return new PaymentIntentResponse(intent.getClientSecret(), amountCents, "usd");
    }

    @Transactional
    public PaymentIntentResponse createBalanceIntent(String buyerId, String orderId) throws StripeException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!buyerId.equals(order.getParticipantId())) {
            throw new IllegalArgumentException("Order does not belong to you");
        }

        if (!"DEPOSIT_PAID".equalsIgnoreCase(order.getStatus()) && !"SHIPPED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Order is not eligible for balance payment");
        }

        Long remaining = order.getRemainingAmountCents();
        if (remaining == null || remaining <= 0) {
            throw new IllegalArgumentException("No remaining balance on this order");
        }

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(remaining)
                .setCurrency(order.getCurrency() != null ? order.getCurrency() : "usd")
                .putMetadata("type", "balance")
                .putMetadata("buyerId", buyerId)
                .putMetadata("orderId", orderId)
                .build();
        PaymentIntent intent = PaymentIntent.create(params);

        order.setBalancePaymentIntentId(intent.getId());
        order.setNotes(appendNote(order.getNotes(), "Balance payment intent created"));
        orderRepository.save(order);

        return new PaymentIntentResponse(intent.getClientSecret(), remaining, order.getCurrency());
    }

    @Transactional
    public void markOrderShipped(String farmerId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!"DEPOSIT_PAID".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Only deposit-paid orders can be marked shipped");
        }

        // Verify farmer owns at least one listing in this order
        List<Map<String, Object>> items;
        try {
            items = objectMapper.readValue(order.getItems(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            throw new RuntimeException("Could not parse order items");
        }

        boolean ownsListing = items.stream()
                .map(i -> i.get("listingId"))
                .filter(Objects::nonNull)
                .map(id -> Long.parseLong(String.valueOf(id)))
                .anyMatch(listingId -> listingRepository.findById(listingId)
                        .map(l -> farmerId.equals(l.getFarmer().getId()))
                        .orElse(false));

        if (!ownsListing) {
            throw new IllegalArgumentException("You are not the seller for this order");
        }

        order.setStatus("SHIPPED");
        order.setNotes(appendNote(order.getNotes(), "Marked shipped by farmer — balance payment due"));
        orderRepository.save(order);
    }
}
