package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean OrderService orderService;
    @MockBean OrderRepository orderRepository;
    @MockBean JwtUtil jwtUtil;

    private void auth(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    // ── GET /api/orders/{orderId} ─────────────────────────────────────────────

    @Test
    void getOrderById_ownerReturns200() throws Exception {
        Order order = new Order();
        order.setId("order-1");
        order.setStatus("PAID");
        order.setParticipantId("buyer-1");

        when(orderService.getOrderById("order-1", "buyer-1")).thenReturn(order);

        auth("buyer-1");
        try {
            mockMvc.perform(get("/api/orders/order-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("order-1"))
                    .andExpect(jsonPath("$.status").value("PAID"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOrderById_unauthorizedRequesterReturns403() throws Exception {
        when(orderService.getOrderById("order-1", "stranger"))
                .thenThrow(new SecurityException("Access denied"));

        auth("stranger");
        try {
            mockMvc.perform(get("/api/orders/order-1"))
                    .andExpect(status().isForbidden());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOrderById_notFoundReturns404() throws Exception {
        when(orderService.getOrderById("missing", "buyer-1"))
                .thenThrow(new IllegalArgumentException("Order not found"));

        auth("buyer-1");
        try {
            mockMvc.perform(get("/api/orders/missing"))
                    .andExpect(status().isNotFound());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getOrderById_noAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/orders/order-1"))
                .andExpect(status().isUnauthorized());
    }
}
