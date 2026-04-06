package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AnimalRequestRequest;
import com.masterchefcuts.dto.AnimalRequestResponse;
import com.masterchefcuts.dto.FulfillRequestBody;
import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.services.AnimalRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnimalRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnimalRequestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AnimalRequestService animalRequestService;
    @MockBean JwtUtil jwtUtil;

    private static final String AUTH_HEADER = "Bearer my-token";
    private AnimalRequestResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = AnimalRequestResponse.builder()
                .id(1L).animalType(AnimalType.BEEF).breed("Angus")
                .zipCode("12345").cutLabels(List.of("Ribeye"))
                .status(AnimalRequestStatus.OPEN).buyerId("buyer-1")
                .buyerName("Bob Buyer").buyerZip("12345")
                .createdAt(LocalDateTime.now()).build();
    }

    // ── POST /api/animal-requests ─────────────────────────────────────────────

    @Test
    void create_returns200WithResponse() throws Exception {
        when(jwtUtil.extractId("my-token")).thenReturn("buyer-1");
        when(animalRequestService.create(eq("buyer-1"), any(AnimalRequestRequest.class)))
                .thenReturn(sampleResponse);

        AnimalRequestRequest req = new AnimalRequestRequest();
        req.setAnimalType(AnimalType.BEEF);
        req.setBreed("Angus");
        req.setZipCode("12345");
        req.setCutLabels(List.of("Ribeye"));

        mockMvc.perform(post("/api/animal-requests")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breed").value("Angus"))
                .andExpect(jsonPath("$.buyerId").value("buyer-1"));
    }

    // ── GET /api/animal-requests ──────────────────────────────────────────────

    @Test
    void getOpen_returns200WithList() throws Exception {
        when(animalRequestService.getOpen()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/animal-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    // ── GET /api/animal-requests/my ───────────────────────────────────────────

    @Test
    void getMine_returns200WithBuyerRequests() throws Exception {
        when(jwtUtil.extractId("my-token")).thenReturn("buyer-1");
        when(animalRequestService.getMyRequests("buyer-1")).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/animal-requests/my")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buyerId").value("buyer-1"));
    }

    // ── POST /api/animal-requests/{id}/fulfill ────────────────────────────────

    @Test
    void fulfill_returns200WithFulfilledResponse() throws Exception {
        AnimalRequestResponse fulfilled = AnimalRequestResponse.builder()
                .id(1L).animalType(AnimalType.BEEF).breed("Angus")
                .zipCode("12345").cutLabels(List.of("Ribeye"))
                .status(AnimalRequestStatus.FULFILLED).buyerId("buyer-1")
                .buyerName("Bob Buyer").buyerZip("12345").fulfilledByFarmerId("farmer-1")
                .fulfilledListingId(10L).createdAt(LocalDateTime.now()).build();

        when(jwtUtil.extractId("my-token")).thenReturn("farmer-1");
        when(animalRequestService.fulfill(eq(1L), eq("farmer-1"), any(FulfillRequestBody.class)))
                .thenReturn(fulfilled);

        FulfillRequestBody body = new FulfillRequestBody();
        body.setWeightLbs(500);
        body.setPricePerLb(12.0);

        mockMvc.perform(post("/api/animal-requests/1/fulfill")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));
    }

    @Test
    void fulfill_serviceThrows_returns400() throws Exception {
        when(jwtUtil.extractId("my-token")).thenReturn("farmer-1");
        when(animalRequestService.fulfill(anyLong(), anyString(), any()))
                .thenThrow(new RuntimeException("Request not found"));

        mockMvc.perform(post("/api/animal-requests/99/fulfill")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightLbs\":500,\"pricePerLb\":10.0}"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/animal-requests/{id} ──────────────────────────────────────

    @Test
    void cancel_returns204() throws Exception {
        when(jwtUtil.extractId("my-token")).thenReturn("buyer-1");
        doNothing().when(animalRequestService).cancel(1L, "buyer-1");

        mockMvc.perform(delete("/api/animal-requests/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancel_serviceThrows_returns400() throws Exception {
        when(jwtUtil.extractId("my-token")).thenReturn("buyer-1");
        doThrow(new RuntimeException("Not authorized")).when(animalRequestService).cancel(1L, "buyer-1");

        mockMvc.perform(delete("/api/animal-requests/1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isBadRequest());
    }
}
