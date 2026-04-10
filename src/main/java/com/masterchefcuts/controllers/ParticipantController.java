package com.masterchefcuts.controllers;

import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.enums.NotificationPreference;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.services.AuthService;
import com.masterchefcuts.services.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
public class ParticipantController {

    @Autowired
    private ParticipantService participantService;

    private final ParticipantRepo participantRepo;
    private final AuthService authService;

    @PostMapping("/add")
    public String add(@RequestBody Participant participant) {
        participantService.addParticipant(participant);
        return "Participant added successfully";
    }

    @PatchMapping("/me/notification-preference")
    public ResponseEntity<AuthResponse> updateNotificationPreference(
            @AuthenticationPrincipal String participantId,
            @RequestBody Map<String, String> body) {
        NotificationPreference pref = NotificationPreference.valueOf(body.get("preference").toUpperCase());
        Participant p = participantRepo.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));
        p.setNotificationPreference(pref);
        participantRepo.save(p);
        return ResponseEntity.ok(authService.getMe(participantId));
    }
}
