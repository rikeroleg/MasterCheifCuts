package com.masterchefcuts.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.ContactRequest;
import com.masterchefcuts.services.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.HttpStatus;

@WebMvcTest(ContactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContactControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean EmailService emailService;
    @MockBean JwtUtil jwtUtil;

    private ContactRequest validRequest() {
        ContactRequest req = new ContactRequest();
        req.setName("Alice Buyer");
        req.setEmail("alice@example.com");
        req.setSubject("Question about a listing");
        req.setMessage("Hi, I wanted to ask about the available cuts for the upcoming Angus listing.");
        return req;
    }

    // ── POST /api/contact ─────────────────────────────────────────────────────

    @Test
    void submitContact_validRequest_returns200AndCallsEmailService() throws Exception {
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        verify(emailService).sendContactFormEmail(
                eq("Alice Buyer"),
                eq("alice@example.com"),
                eq("Question about a listing"),
                anyString()
        );
    }

    @Test
    void submitContact_missingName_returns422() throws Exception {
        ContactRequest req = validRequest();
        req.setName("");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(emailService);
    }

    @Test
    void submitContact_invalidEmail_returns422() throws Exception {
        ContactRequest req = validRequest();
        req.setEmail("not-an-email");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(emailService);
    }

    @Test
    void submitContact_blankMessage_returns422() throws Exception {
        ContactRequest req = validRequest();
        req.setMessage("  ");

        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(emailService);
    }

    @Test
    void submitContact_missingBody_returns422() throws Exception {
        mockMvc.perform(post("/api/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(emailService);
    }
}
