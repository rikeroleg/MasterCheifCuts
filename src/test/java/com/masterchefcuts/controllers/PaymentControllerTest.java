package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.CartPaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.services.PaymentService;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PaymentService paymentService;
    @MockBean JwtUtil jwtUtil;

    private final PaymentIntentResponse SAMPLE =
            new PaymentIntentResponse("pi_secret_123", 20000L, "usd");

    private UsernamePasswordAuthenticationToken buyerAuth() {
        return new UsernamePasswordAuthenticationToken("buyer-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
    }

    // ── POST /api/payments/intent ─────────────────────────────────────────────

    @Test
    void createIntent_returns200WithClientSecret() throws Exception {
        when(paymentService.createIntent(any(PaymentIntentRequest.class))).thenReturn(SAMPLE);

        PaymentIntentRequest req = new PaymentIntentRequest();
        req.setListingId(1L);
        req.setCutLabel("Ribeye");

        mockMvc.perform(post("/api/payments/intent")
                        .with(authentication(buyerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_secret_123"))
                .andExpect(jsonPath("$.currency").value("usd"));
    }

    @Test
    void createIntent_stripeThrows_returns400() throws Exception {
        when(paymentService.createIntent(any())).thenThrow(new IllegalArgumentException("Listing not found"));

        mockMvc.perform(post("/api/payments/intent")
                        .with(authentication(buyerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":99,\"cutLabel\":\"Ribeye\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/payments/cart-intent ────────────────────────────────────────

    @Test
    void createCartIntent_returns200() throws Exception {
        when(paymentService.createCartIntent(eq("buyer-1"), eq(List.of(10L, 11L)), eq("FULL")))
            .thenReturn(new PaymentIntentResponse("pi_cart_secret", 5000L, "usd"));

        CartPaymentIntentRequest req = new CartPaymentIntentRequest();
        req.setCutIds(List.of(10L, 11L));

        mockMvc.perform(post("/api/payments/cart-intent")
                        .with(authentication(buyerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_cart_secret"));
    }

    // ── POST /api/payments/webhook ───────────────────────────────────────────

    @Test
    void handleStripeWebhook_returns200() throws Exception {
        doNothing().when(paymentService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=123,v1=sig")
                        .content("{\"type\":\"payment_intent.succeeded\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(paymentService).handleWebhook(anyString(), eq("t=123,v1=sig"));
    }
}
