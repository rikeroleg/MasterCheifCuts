package com.masterchefcuts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.services.AdminSettingsService;
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
import com.masterchefcuts.model.Participant;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Charge;
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
    private final StripeConnectService stripeConnectService;
    private final RefundService refundService;
    private final EmailService emailService;
    private final AdminSettingsService adminSettingsService;

    /** Platform fee percentage taken from the sale price. */
    private static final double PLATFORM_FEE_RATE = 0.15;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Transactional
    public PaymentIntentResponse createCartIntent(String buyerId, List<Long> cutIds) throws StripeException {
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
        // Track unique farmers so we can set up transfer_data when all cuts belong to one farmer
        java.util.Set<String> farmerIds = new java.util.LinkedHashSet<>();
        Map<String, Long> farmerAmounts = new HashMap<>();

        for (Cut cut : cuts) {
            if (!cut.isClaimed() || cut.getClaimedBy() == null || !buyerId.equals(cut.getClaimedBy().getId())) {
                throw new IllegalArgumentException("Cart contains cuts not claimed by the current user");
            }

            Listing listing = cut.getListing();
            long totalCuts = totalCutsByListing.computeIfAbsent(listing.getId(), cutRepository::countByListingId);
            if (totalCuts <= 0) {
                throw new IllegalArgumentException("Listing has no cuts");
            }

            long cutAmountCents = (cut.getWeightLbs() != null && cut.getWeightLbs() > 0)
                    ? Math.round(cut.getWeightLbs() * listing.getPricePerLb() * 100)
                    : Math.round((listing.getPricePerLb() * listing.getWeightLbs() / totalCuts) * 100);
            amountCents += cutAmountCents;

            String farmerId = listing.getFarmer().getId();
            farmerIds.add(farmerId);
            farmerAmounts.merge(farmerId, cutAmountCents, Long::sum);

            Map<String, Object> item = new HashMap<>();
            item.put("cutId", cut.getId());
            item.put("cutLabel", cut.getLabel());
            item.put("listingId", listing.getId());
            item.put("animalType", String.valueOf(listing.getAnimalType()));
            item.put("breed", listing.getBreed());
            item.put("amountCents", cutAmountCents);
            item.put("farmerId", farmerId);
            orderItems.add(item);
        }

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Cart total must be greater than zero");
        }

        // Generate idempotency key based on buyer and cuts
        // This ensures the same request won't create duplicate PaymentIntents
        String idempotencyKey = generateIdempotencyKey(buyerId, distinctCutIds);

        // ── Stripe Connect: 15% platform fee → auto-transfer 85% to farmer ──
        // Only supported when all cuts belong to a single fully-onboarded farmer.
        String singleFarmerConnectId = null;
        if (farmerIds.size() == 1) {
            String singleFarmerId = farmerIds.iterator().next();
            Optional<Participant> farmerOpt = participantRepo.findById(singleFarmerId);
            if (farmerOpt.isPresent()
                    && Boolean.TRUE.equals(farmerOpt.get().getStripeOnboardingComplete())
                    && farmerOpt.get().getStripeAccountId() != null) {
                singleFarmerConnectId = farmerOpt.get().getStripeAccountId();
            }
        } else {
            log.warn("Cart contains cuts from {} farmers — automatic payout transfer skipped; manual payout required", farmerIds.size());
        }

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .putMetadata("type", "cart")
                .putMetadata("buyerId", buyerId)
                .putMetadata("paymentType", "FULL")
                .putMetadata("cutCount", String.valueOf(distinctCutIds.size()))
                .putMetadata("cutIds", distinctCutIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""))
                .putMetadata("idempotencyKey", idempotencyKey);

        if (singleFarmerConnectId != null) {
            long platformFeeCents = Math.round(amountCents * PLATFORM_FEE_RATE);
            paramsBuilder
                    .setApplicationFeeAmount(platformFeeCents)
                    .setTransferData(PaymentIntentCreateParams.TransferData.builder()
                            .setDestination(singleFarmerConnectId)
                            .build());
            log.info("Stripe Connect transfer configured: farmer account={}, fee={}c ({:.0f}%)",
                    singleFarmerConnectId, platformFeeCents, PLATFORM_FEE_RATE * 100);
        }

        PaymentIntentCreateParams params = paramsBuilder.build();

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
        order.setNotes("Awaiting payment confirmation from Stripe webhook");

        // Snapshot buyer delivery address at order creation time
        participantRepo.findById(buyerId).ifPresent(buyer -> {
            order.setDeliveryStreet(buyer.getStreet() != null ? (buyer.getApt() != null && !buyer.getApt().isBlank()
                    ? buyer.getStreet() + ", " + buyer.getApt() : buyer.getStreet()) : null);
            order.setDeliveryCity(buyer.getCity());
            order.setDeliveryState(buyer.getState());
            order.setDeliveryZip(buyer.getZipCode());
        });

        orderRepository.save(order);

        return new PaymentIntentResponse(intent.getClientSecret(), amountCents, "usd");
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

        // Extract paymentIntentId for the idempotency record (null for non-PaymentIntent events)
        String intentIdForRecord = (stripeObject instanceof PaymentIntent pi) ? pi.getId() : null;

        // Record that we're processing this event (with unique constraint protection)
        try {
            WebhookEvent webhookEvent = WebhookEvent.builder()
                    .stripeEventId(event.getId())
                    .eventType(event.getType())
                    .paymentIntentId(intentIdForRecord)
                    .build();
            webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            // Another thread already recorded this event - skip processing
            log.info("Webhook event {} already being processed by another thread", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                if (stripeObject instanceof PaymentIntent intent) markOrderPaid(intent, event.getId());
            }
            case "payment_intent.payment_failed", "payment_intent.canceled" -> {
                if (stripeObject instanceof PaymentIntent intent) markOrderFailed(intent, event.getId());
            }
            case "charge.refunded" -> {
                // Sync refunds initiated from the Stripe dashboard
                if (stripeObject instanceof Charge charge && charge.getPaymentIntent() != null) {
                    long amountRefunded = charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L;
                    refundService.handleChargeRefunded(charge.getPaymentIntent(), amountRefunded);
                }
            }
            case "charge.dispute.created" -> {
                if (stripeObject instanceof com.stripe.model.Dispute dispute
                        && dispute.getPaymentIntent() != null) {
                    long disputeAmount = dispute.getAmount() != null ? dispute.getAmount() : 0L;
                    refundService.handleDisputeCreated(dispute.getPaymentIntent(), dispute.getId(), disputeAmount);
                }
            }
            default -> {
                // ignore unsupported payment events
            }
        }
    }

    @Transactional
    public void handleAccountWebhook(String payload, String stripeSignatureHeader) {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }
        final Event event;
        try {
            event = Webhook.constructEvent(payload, stripeSignatureHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature");
        }

        if ("account.updated".equals(event.getType())) {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = deserializer.getObject().orElse(null);
            if (stripeObject instanceof Account account) {
                stripeConnectService.handleAccountUpdated(account.getId());
            }
        }
    }

    private void markOrderPaid(PaymentIntent intent, String eventId) {
        // Use pessimistic locking to prevent race conditions
        Optional<Order> orderOpt = orderRepository.findByStripePaymentIntentIdForUpdate(intent.getId());
        
        Order order = orderOpt.orElseGet(() -> createFallbackOrder(intent));

        // Idempotency check: skip if already paid
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} already marked as PAID, skipping duplicate webhook {}", order.getId(), eventId);
            return;
        }

        long confirmedAmount = intent.getAmountReceived() != null && intent.getAmountReceived() > 0
                ? intent.getAmountReceived()
                : intent.getAmount();

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

        orderRepository.save(order);

        // Mark associated claims as paid so they won't be expired
        markClaimsPaidForOrder(order);

        // Notify farmers of the new paid order
        notifyFarmersOfPayment(order);

        // Send order confirmation emails
        sendOrderEmails(order);

        if ("PAID".equalsIgnoreCase(order.getStatus())
                && order.getParticipantId() != null && !order.getParticipantId().isBlank()) {
            participantRepo.findById(order.getParticipantId()).ifPresent(p -> {
                p.setTotalSpent(p.getTotalSpent() + order.getTotalAmount());
                participantRepo.save(p);
            });
        }
    }

    private void sendOrderEmails(Order order) {
        try {
            // Email to buyer
            if (order.getParticipantId() != null && !order.getParticipantId().isBlank()) {
                participantRepo.findById(order.getParticipantId()).ifPresent(buyer ->
                        emailService.sendOrderConfirmationToBuyer(order, buyer));
            }

            // Email to each unique farmer for listings in this order
            String itemsJson = order.getItems();
            if (itemsJson == null || itemsJson.isBlank()) return;
            List<Map<String, Object>> items = objectMapper.readValue(itemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            items.stream()
                    .map(i -> i.get("listingId"))
                    .filter(Objects::nonNull)
                    .map(id -> Long.parseLong(String.valueOf(id)))
                    .distinct()
                    .forEach(listingId -> listingRepository.findById(listingId).ifPresent(listing -> {
                        Participant farmer = listing.getFarmer();
                        if (farmer != null) {
                            emailService.sendNewOrderToFarmer(order, listing, farmer);
                        }
                    }));
        } catch (Exception e) {
            log.warn("Failed to send order emails for order {}: {}", order.getId(), e.getMessage());
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

        // Notify all admin users of the new paid order
        if (adminSettingsService.isAdminOrderNotificationsEnabled()) {
            try {
                String amount = String.format("$%.2f", order.getTotalAmount());
                participantRepo.findByRole(Role.ADMIN).forEach(admin ->
                    notificationService.send(admin,
                            com.masterchefcuts.enums.NotificationType.ORDER_PAID,
                            "🛒",
                            "New Order Placed",
                            "A buyer placed a new paid order totalling " + amount + ".",
                            null,
                            order.getId()));
            } catch (Exception e) {
                // Don't fail the payment flow if admin notification fails
            }
        }
    }

    private void markOrderFailed(PaymentIntent intent, String eventId) {
        // Use pessimistic locking
        Optional<Order> orderOpt = orderRepository.findByStripePaymentIntentIdForUpdate(intent.getId());
        
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
    private String generateIdempotencyKey(String buyerId, List<Long> cutIds) {
        // Sort cut IDs to ensure consistent ordering
        List<Long> sortedCutIds = cutIds.stream().sorted().toList();
        String cutString = sortedCutIds.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        
        // Create a hash of the key components
        String keyInput = buyerId + "|" + cutString + "|FULL|" + 
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
            return "cart_" + buyerId + "_" + sortedCutIds.hashCode() + "_FULL";
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
}
