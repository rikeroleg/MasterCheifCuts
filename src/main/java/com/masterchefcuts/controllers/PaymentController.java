package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.CartPaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.services.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/payments/intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(@RequestBody PaymentIntentRequest request) throws StripeException {
        return ResponseEntity.ok(paymentService.createIntent(request));
    }

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/api/payments/cart-intent")
    public ResponseEntity<PaymentIntentResponse> createCartIntent(
            @AuthenticationPrincipal String buyerId,
            @RequestBody CartPaymentIntentRequest request) throws StripeException {
        return ResponseEntity.ok(paymentService.createCartIntent(buyerId, request.getCutIds()));
    }

    @PostMapping(value = "/api/payments/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignatureHeader) {
        paymentService.handleWebhook(payload, stripeSignatureHeader);
        return ResponseEntity.ok("ok");
    }

    /**
     * Receives Stripe Connect account.updated events.
     * Configure this as a separate webhook endpoint in the Stripe dashboard
     * pointing to /api/payments/connect-webhook.
     */
    @PostMapping(value = "/api/payments/connect-webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleStripeConnectWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignatureHeader) {
        paymentService.handleAccountWebhook(payload, stripeSignatureHeader);
        return ResponseEntity.ok("ok");
    }
}
