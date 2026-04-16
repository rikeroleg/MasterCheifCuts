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

    // ── GET /api/orders/my ────────────────────────────────────────────────────

    @Test
    void myOrders_returns200WithList() throws Exception {
        Order order = new Order();
        order.setId("order-1"); order.setStatus("PAID");
        when(orderRepository.findByParticipantIdOrderByOrderDateDesc("buyer-1")).thenReturn(List.of(order));

        auth("buyer-1");
        try {
            mockMvc.perform(get("/api/orders/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("order-1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void myOrders_emptyList_returns200() throws Exception {
        when(orderRepository.findByParticipantIdOrderByOrderDateDesc("buyer-1")).thenReturn(List.of());
        auth("buyer-1");
        try {
            mockMvc.perform(get("/api/orders/my")).andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/orders/farmer ────────────────────────────────────────────────

    @Test
    void farmerOrders_returns200WithList() throws Exception {
        Order order = new Order();
        order.setId("order-2"); order.setStatus("ACCEPTED");
        when(orderService.getFarmerOrders("farmer-1")).thenReturn(List.of(order));

        auth("farmer-1");
        try {
            mockMvc.perform(get("/api/orders/farmer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("order-2"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── PATCH /api/orders/{orderId}/status ────────────────────────────────────

    @Test
    void updateStatus_returns200WithUpdatedOrder() throws Exception {
        Order order = new Order();
        order.setId("order-1"); order.setStatus("ACCEPTED");
        when(orderService.updateOrderStatus("farmer-1", "order-1", "ACCEPTED")).thenReturn(order);

        auth("farmer-1");
        try {
            mockMvc.perform(patch("/api/orders/order-1/status")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        auth("farmer-1");
        try {
            mockMvc.perform(patch("/api/orders/order-1/status")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── POST /api/orders/{orderId}/confirm-receipt ────────────────────────────

    @Test
    void confirmReceipt_returns200WithOrder() throws Exception {
        Order order = new Order();
        order.setId("order-1"); order.setStatus("COMPLETED");
        when(orderService.confirmReceipt("buyer-1", "order-1")).thenReturn(order);

        auth("buyer-1");
        try {
            mockMvc.perform(post("/api/orders/order-1/confirm-receipt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmReceipt_serviceThrows_returns400() throws Exception {
        when(orderService.confirmReceipt("buyer-1", "order-1"))
                .thenThrow(new IllegalArgumentException("Order must be in READY status"));

        auth("buyer-1");
        try {
            mockMvc.perform(post("/api/orders/order-1/confirm-receipt"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmReceipt_emptyAuth_returnsOkOrError() throws Exception {
        Order order = new Order(); order.setId("order-1"); order.setStatus("COMPLETED");
        when(orderService.confirmReceipt(null, "order-1")).thenReturn(order);
        // No auth context set - principal will be null, service decides behavior
        mockMvc.perform(post("/api/orders/order-1/confirm-receipt"))
                .andExpect(status().isOk());
    }
}
