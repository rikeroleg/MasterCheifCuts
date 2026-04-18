package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.ContactRequest;
import com.masterchefcuts.services.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    @PostMapping("/api/contact")
    public ResponseEntity<Map<String, String>> submitContactForm(
            @Valid @RequestBody ContactRequest req) {
        emailService.sendContactFormEmail(req.getName(), req.getEmail(), req.getSubject(), req.getMessage());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
