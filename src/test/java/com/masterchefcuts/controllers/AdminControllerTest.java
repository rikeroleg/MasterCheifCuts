package com.masterchefcuts.controllers;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean AdminService adminService;
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
}
