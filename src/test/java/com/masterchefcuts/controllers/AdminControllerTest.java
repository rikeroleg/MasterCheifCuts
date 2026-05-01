package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.AdminService;
import com.masterchefcuts.services.AdminSettingsService;
import com.masterchefcuts.services.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AdminService adminService;
    @MockBean ReviewService reviewService;
    @MockBean AdminSettingsService adminSettingsService;
    @MockBean JwtUtil jwtUtil;

    private static final Participant FARMER = Participant.builder()
            .id("farmer-1").firstName("Jane").lastName("Farm")
            .role(Role.FARMER).email("jane@farm.com").password("pass")
            .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
            .status("ACTIVE").approved(true).build();

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken("admin-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────────

    @Test
    void getAllUsers_returns200WithList() throws Exception {
        when(adminService.getAllUsers()).thenReturn(List.of(FARMER));

        mockMvc.perform(get("/api/admin/users").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("farmer-1"));
    }

    // ── PATCH /api/admin/users/{id}/approve ───────────────────────────────────

    @Test
    void approve_returns200WithApprovedParticipant() throws Exception {
        when(adminService.setApproved("farmer-1", true)).thenReturn(FARMER);

        mockMvc.perform(patch("/api/admin/users/farmer-1/approve").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("farmer-1"));
    }

    @Test
    void approve_notFound_returns400() throws Exception {
        when(adminService.setApproved("missing", true)).thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(patch("/api/admin/users/missing/approve").with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/admin/users/{id}/reject ────────────────────────────────────

    @Test
    void reject_returns200() throws Exception {
        Participant rejected = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(false).build();
        when(adminService.setApproved("farmer-1", false)).thenReturn(rejected);

        mockMvc.perform(patch("/api/admin/users/farmer-1/reject").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false));
    }

    // ── DELETE /api/admin/listings/{id} ───────────────────────────────────────

    @Test
    void deleteListing_returns204() throws Exception {
        doNothing().when(adminService).deleteListing(1L);

        mockMvc.perform(delete("/api/admin/listings/1").with(authentication(adminAuth())))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/admin/stats ──────────────────────────────────────────────────

    @Test
    void getStats_returns200WithStats() throws Exception {
        when(adminService.getStats()).thenReturn(Map.of(
                "totalUsers", 5L, "totalListings", 3L,
                "totalClaims", 10L, "pendingFarmers", 1L));

        mockMvc.perform(get("/api/admin/stats").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(5));
    }
    // ── GET /api/admin/comments ───────────────────────────────────────

    @Test
    void getComments_returns200WithPagedResponse() throws Exception {
        Map<String, Object> paged = Map.of(
                "content", List.of(Map.of("id", 1, "body", "Great beef!", "authorName", "Bob B.")),
                "page", 0, "size", 25, "totalElements", 1L, "hasNext", false);
        when(adminService.getCommentsPaged(0, 25)).thenReturn(paged);

        mockMvc.perform(get("/api/admin/comments").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getComments_withCustomPage_passesParams() throws Exception {
        when(adminService.getCommentsPaged(2, 10)).thenReturn(Map.of(
                "content", List.of(), "page", 2, "size", 10, "totalElements", 0L, "hasNext", false));

        mockMvc.perform(get("/api/admin/comments")
                        .param("page", "2").param("size", "10")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/admin/comments/{id} ────────────────────────────────

    @Test
    void deleteComment_returns204() throws Exception {
        doNothing().when(adminService).adminDeleteComment(1L);

        mockMvc.perform(delete("/api/admin/comments/1").with(authentication(adminAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteComment_serviceThrows_returns400() throws Exception {
        doThrow(new RuntimeException("Comment not found")).when(adminService).adminDeleteComment(99L);

        mockMvc.perform(delete("/api/admin/comments/99").with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/admin/users/{id} ─────────────────────────────────────────────

    @Test
    void getUserDetail_returns200WithUserMap() throws Exception {
        Map<String, Object> detail = Map.of("id", "farmer-1", "email", "jane@farm.com", "orders", List.of());
        when(adminService.getUserDetail("farmer-1")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/users/farmer-1").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("farmer-1"))
                .andExpect(jsonPath("$.email").value("jane@farm.com"));
    }

    @Test
    void getUserDetail_serviceThrows_returns400() throws Exception {
        when(adminService.getUserDetail("nobody")).thenThrow(new RuntimeException("User not found: nobody"));

        mockMvc.perform(get("/api/admin/users/nobody").with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────

    @Test
    void getAllOrders_returns200WithList() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("ord-1");
        order.setStatus("PAID");
        when(adminService.getAllOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/admin/orders").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ord-1"));
    }

    // ── POST /api/admin/orders/{id}/refund ────────────────────────────────────

    @Test
    void refundOrder_returns200WithOrder() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("ord-1");
        order.setStatus("REFUNDED");
        when(adminService.issueRefund(eq("ord-1"), anyString())).thenReturn(order);

        mockMvc.perform(post("/api/admin/orders/ord-1/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Buyer requested")))
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ord-1"));
    }

    @Test
    void refundOrder_serviceThrows_returns400() throws Exception {
        when(adminService.issueRefund(anyString(), anyString()))
                .thenThrow(new RuntimeException("Order not found"));

        mockMvc.perform(post("/api/admin/orders/bad-id/refund")
                        .param("reason", "Test")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/admin/financials/summary ─────────────────────────────────────

    @Test
    void getFinancialSummary_returns200WithSummary() throws Exception {
        Map<String, Object> summary = Map.of("totalRevenue", 500.0, "orderCount", 3);
        when(adminService.getFinancialSummary(null, null)).thenReturn(summary);

        mockMvc.perform(get("/api/admin/financials/summary").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(500.0));
    }

    @Test
    void getFinancialSummary_withDateParams_passesThemToService() throws Exception {
        Map<String, Object> summary = Map.of("totalRevenue", 100.0, "orderCount", 1);
        when(adminService.getFinancialSummary("2026-01-01", "2026-02-01")).thenReturn(summary);

        mockMvc.perform(get("/api/admin/financials/summary")
                        .param("from", "2026-01-01")
                        .param("to", "2026-02-01")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(1));
    }

    // ── GET /api/admin/financials/orders ──────────────────────────────────────

    @Test
    void getFinancialOrders_returns200WithList() throws Exception {
        List<Map<String, Object>> orders = List.of(Map.of("id", "ord-1", "status", "PAID"));
        when(adminService.getFinancialOrders(null, null, null)).thenReturn(orders);

        mockMvc.perform(get("/api/admin/financials/orders").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ord-1"));
    }
}
