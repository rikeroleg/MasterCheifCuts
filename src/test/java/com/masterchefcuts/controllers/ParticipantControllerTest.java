package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ReviewRepository;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.OrderService;
import com.masterchefcuts.services.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ParticipantController.class)
@AutoConfigureMockMvc(addFilters = false)
class ParticipantControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ParticipantService participantService;
    @MockBean JwtUtil jwtUtil;
    @MockBean ParticipantRepo participantRepo;
    @MockBean AuthService authService;
    @MockBean ListingRepository listingRepository;
    @MockBean OrderService orderService;
    @MockBean ReviewRepository reviewRepository;

    private void auth(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void add_returns200SuccessMessage() throws Exception {
        Participant participant = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        when(participantService.addParticipant(any(Participant.class))).thenReturn(participant);

        mockMvc.perform(post("/api/participants/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participant)))
                .andExpect(status().isOk())
                .andExpect(content().string("Participant added successfully"));
    }

    // ── GET /api/participants/me/analytics ──────────────────────────────────

    @Test
    void getMyAnalytics_emptyData_returnsZeros() throws Exception {
        when(listingRepository.findByFarmerIdOrderByPostedAtDesc("farmer-1")).thenReturn(List.of());
        when(orderService.getFarmerOrders("farmer-1")).thenReturn(List.of());
        when(reviewRepository.findByListingFarmerIdOrderByCreatedAtDesc("farmer-1")).thenReturn(List.of());

        auth("farmer-1");
        try {
            mockMvc.perform(get("/api/participants/me/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalListings").value(0))
                    .andExpect(jsonPath("$.activeListings").value(0))
                    .andExpect(jsonPath("$.totalCutsClaimed").value(0))
                    .andExpect(jsonPath("$.totalRevenue").value(0.0))
                    .andExpect(jsonPath("$.averageRating").value(0.0))
                    .andExpect(jsonPath("$.totalReviews").value(0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getMyAnalytics_withCompletedOrder_includesRevenue() throws Exception {
        when(listingRepository.findByFarmerIdOrderByPostedAtDesc("farmer-1")).thenReturn(List.of());

        Order completedOrder = new Order();
        completedOrder.setStatus("COMPLETED");
        completedOrder.setAmountCents(5000L); // $50.00
        when(orderService.getFarmerOrders("farmer-1")).thenReturn(List.of(completedOrder));
        when(reviewRepository.findByListingFarmerIdOrderByCreatedAtDesc("farmer-1")).thenReturn(List.of());

        auth("farmer-1");
        try {
            mockMvc.perform(get("/api/participants/me/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRevenue").value(50.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void getMyAnalytics_nonCompletedOrdersExcludedFromRevenue() throws Exception {
        when(listingRepository.findByFarmerIdOrderByPostedAtDesc("farmer-1")).thenReturn(List.of());

        Order paidOrder = new Order();
        paidOrder.setStatus("PAID");
        paidOrder.setAmountCents(10000L);
        when(orderService.getFarmerOrders("farmer-1")).thenReturn(List.of(paidOrder));
        when(reviewRepository.findByListingFarmerIdOrderByCreatedAtDesc("farmer-1")).thenReturn(List.of());

        auth("farmer-1");
        try {
            mockMvc.perform(get("/api/participants/me/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRevenue").value(0.0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── PATCH /api/participants/me/notification-preference ───────────────────────

    @Test
    void updateNotificationPreference_returns200() throws Exception {
        Participant p = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(p));

        com.masterchefcuts.dto.AuthResponse authResp = com.masterchefcuts.dto.AuthResponse.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .email("bob@buyer.com").role(Role.BUYER).approved(true).build();
        when(authService.getMe("buyer-1")).thenReturn(authResp);

        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/notification-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preference\":\"ALL\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("buyer-1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateNotificationPreference_participantNotFound_returns400() throws Exception {
        when(participantRepo.findById("buyer-2")).thenReturn(Optional.empty());

        auth("buyer-2");
        try {
            mockMvc.perform(patch("/api/participants/me/notification-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preference\":\"ALL\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateNotificationPreference_missingKey_returns400() throws Exception {
        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/notification-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateNotificationPreference_invalidValue_returns400() throws Exception {
        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/notification-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"preference\":\"NOT_A_VALID_ENUM\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── PATCH /api/participants/me/email-preference ───────────────────────────────────

    @Test
    void updateEmailPreference_returns200() throws Exception {
        Participant p = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(p));

        com.masterchefcuts.dto.AuthResponse authResp = com.masterchefcuts.dto.AuthResponse.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .email("bob@buyer.com").role(Role.BUYER).approved(true).build();
        when(authService.getMe("buyer-1")).thenReturn(authResp);

        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/email-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailPreference\":\"IMPORTANT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("buyer-1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateEmailPreference_invalidValue_returns400() throws Exception {
        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/email-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailPreference\":\"GARBAGE\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void updateEmailPreference_missingKey_returns400() throws Exception {
        auth("buyer-1");
        try {
            mockMvc.perform(patch("/api/participants/me/email-preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── GET /api/participants/{id}/public ────────────────────────────────────

    @Test
    void getPublicProfile_returns200_forFarmer() throws Exception {
        Participant farmer = Participant.builder()
                .id("farmer-1").firstName("John").lastName("Smith")
                .role(Role.FARMER).email("john@farm.com").password("pass")
                .shopName("Smith Ranch").bio("Grass-fed beef").certifications("USDA Organic")
                .zipCode("90210").status("ACTIVE").approved(true).build();
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));

        mockMvc.perform(get("/api/participants/farmer-1/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("farmer-1"))
                .andExpect(jsonPath("$.name").value("John Smith"))
                .andExpect(jsonPath("$.shopName").value("Smith Ranch"))
                .andExpect(jsonPath("$.bio").value("Grass-fed beef"))
                .andExpect(jsonPath("$.certifications").value("USDA Organic"))
                .andExpect(jsonPath("$.zipCode").value("90210"));
    }

    @Test
    void getPublicProfile_returns404_forBuyer() throws Exception {
        Participant buyer = Participant.builder()
                .id("buyer-99").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .status("ACTIVE").approved(true).build();
        when(participantRepo.findById("buyer-99")).thenReturn(Optional.of(buyer));

        mockMvc.perform(get("/api/participants/buyer-99/public"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPublicProfile_returns404_whenNotFound() throws Exception {
        when(participantRepo.findById("ghost-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/participants/ghost-id/public"))
                .andExpect(status().isNotFound());
    }
}
