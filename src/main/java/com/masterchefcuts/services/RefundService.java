package com.masterchefcuts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.enums.NotificationType;
import com.masterchefcuts.model.Claim;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    private final OrderRepository orderRepository;
    private final ParticipantRepo participantRepo;
    private final ClaimRepository claimRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Admin-initiated refund. Issues a Stripe refund and updates the order to REFUNDED.
     * Reverses buyer totalSpent and un-marks claims as paid so cuts become available again.
     *
     * @param orderId  UUID of the order to refund
     * @param reason   Human-readable reason stored in order notes (e.g. "Customer complaint")
     * @param fullRefund  true = refund the full captured amount; false = refund only what was charged
     */
    @Transactional
    public Order issueRefund(String orderId, String reason, boolean fullRefund) throws StripeException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Order has already been refunded");
        }

        if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())
                || "PAYMENT_FAILED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Cannot refund an order that was never paid");
        }

        if (order.getStripePaymentIntentId() == null) {
            throw new IllegalArgumentException("Order has no associated Stripe payment");
        }

        // Retrieve PaymentIntent to get the charge ID
        com.stripe.model.PaymentIntent intent =
                com.stripe.model.PaymentIntent.retrieve(order.getStripePaymentIntentId());

        String chargeId = intent.getLatestCharge();
        if (chargeId == null || chargeId.isBlank()) {
            throw new IllegalArgumentException("No charge found for this payment — refund cannot be issued automatically");
        }

        long refundAmount = fullRefund ? (order.getAmountCents() != null ? order.getAmountCents() : 0L) : 0L;

        RefundCreateParams.Builder refundParams = RefundCreateParams.builder()
                .setCharge(chargeId)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);

        if (fullRefund && refundAmount > 0) {
            refundParams.setAmount(refundAmount);
        }

        Refund refund = Refund.create(refundParams.build());
        log.info("Stripe refund {} issued for order {} (charge={}, amount={}c)",
                refund.getId(), orderId, chargeId, refund.getAmount());

        // Update order status
        order.setStatus("REFUNDED");
        order.setNotes(appendNote(order.getNotes(),
                String.format("Refunded %s via Stripe (refund=%s). Reason: %s",
                        fullRefund ? "$" + String.format("%.2f", refund.getAmount() / 100.0) : "partial",
                        refund.getId(), reason)));
        orderRepository.save(order);

        // Reverse buyer's totalSpent
        if (order.getParticipantId() != null && refund.getAmount() != null && refund.getAmount() > 0) {
            participantRepo.findById(order.getParticipantId()).ifPresent(buyer -> {
                double refundedDollars = refund.getAmount() / 100.0;
                buyer.setTotalSpent(Math.max(0, buyer.getTotalSpent() - refundedDollars));
                participantRepo.save(buyer);
            });
        }

        // Un-mark claims as paid so the cuts become available again
        reverseClaimPayments(order);

        // Notify buyer
        if (order.getParticipantId() != null) {
            participantRepo.findById(order.getParticipantId()).ifPresent(buyer -> {
                String amount = refund.getAmount() != null
                        ? String.format("$%.2f", refund.getAmount() / 100.0) : "your payment";
                notificationService.send(
                        buyer,
                        NotificationType.ORDER_REFUNDED,
                        "💸",
                        "Refund Issued",
                        "A refund of " + amount + " has been issued to your payment method. "
                                + "It may take 5–10 business days to appear.",
                        null,
                        orderId
                );
            });
        }

        return order;
    }

    /**
     * Called from the webhook when Stripe sends charge.refunded.
     * Used to sync status for refunds initiated directly in the Stripe dashboard.
     */
    @Transactional
    public void handleChargeRefunded(String paymentIntentId, long amountRefunded) {
        orderRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(order -> {
            if ("REFUNDED".equalsIgnoreCase(order.getStatus())) return; // already handled

            order.setStatus("REFUNDED");
            order.setNotes(appendNote(order.getNotes(),
                    String.format("Refund confirmed by Stripe webhook (amount=%dc)", amountRefunded)));
            orderRepository.save(order);

            // Reverse buyer totalSpent
            if (order.getParticipantId() != null && amountRefunded > 0) {
                participantRepo.findById(order.getParticipantId()).ifPresent(buyer -> {
                    buyer.setTotalSpent(Math.max(0, buyer.getTotalSpent() - amountRefunded / 100.0));
                    participantRepo.save(buyer);
                });
            }

            reverseClaimPayments(order);

            // Notify buyer
            participantRepo.findById(order.getParticipantId() != null ? order.getParticipantId() : "").ifPresent(buyer -> {
                notificationService.send(
                        buyer,
                        NotificationType.ORDER_REFUNDED,
                        "💸",
                        "Refund Issued",
                        String.format("A refund of $%.2f has been issued to your payment method.", amountRefunded / 100.0),
                        null,
                        order.getId()
                );
            });

            log.info("Order {} marked REFUNDED via webhook (paymentIntent={})", order.getId(), paymentIntentId);
        });
    }

    /**
     * Called from the webhook when Stripe sends charge.dispute.created.
     * Marks the order as DISPUTED and notifies admins.
     */
    @Transactional
    public void handleDisputeCreated(String paymentIntentId, String disputeId, long disputeAmount) {
        orderRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(order -> {
            if ("DISPUTED".equalsIgnoreCase(order.getStatus())) return;

            order.setStatus("DISPUTED");
            order.setNotes(appendNote(order.getNotes(),
                    String.format("Stripe chargeback/dispute opened (dispute=%s, amount=%dc). Action required in Stripe dashboard.",
                            disputeId, disputeAmount)));
            orderRepository.save(order);
            log.warn("Order {} flagged as DISPUTED (dispute={}, paymentIntent={})", order.getId(), disputeId, paymentIntentId);
        });
    }

    // ──────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────

    private void reverseClaimPayments(Order order) {
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
                List<Claim> claims = claimRepository.findByCutIdIn(cutIds);
                for (Claim claim : claims) {
                    claim.setPaid(false);
                }
                claimRepository.saveAll(claims);
                log.info("Reversed paid status on {} claim(s) for order {}", claims.size(), order.getId());
            }
        } catch (Exception e) {
            log.error("Failed to reverse claim payments for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private String appendNote(String existing, String note) {
        if (existing == null || existing.isBlank()) return note;
        return existing + " | " + note;
    }
}
