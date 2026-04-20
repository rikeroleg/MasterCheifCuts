package com.masterchefcuts.services;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.dto.LoginRequest;
import com.masterchefcuts.dto.RegisterRequest;
import com.masterchefcuts.dto.UpdateProfileRequest;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import lombok.RequiredArgsConstructor;
import com.masterchefcuts.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    @Value("${features.email-verification:false}")
    private boolean emailVerificationEnabled;

    private final ParticipantRepo participantRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ReferralService referralService;

    @Transactional
    public AuthResponse register(RegisterRequest req, EmailService emailService) {
        java.util.Optional<Participant> existing = participantRepo.findByEmail(req.getEmail());
        if (existing.isPresent()) {
            Participant ex = existing.get();
            if (emailVerificationEnabled && !ex.isEmailVerified()) {
                String newToken = UUID.randomUUID().toString();
                ex.setVerificationToken(newToken);
                participantRepo.save(ex);
                emailService.sendEmailVerification(ex.getEmail(), ex.getFirstName(), newToken);
                return buildResponse(ex, null);
            }
            throw new AppException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }

        boolean isFarmer = req.getRole() == Role.FARMER;

        Participant participant = Participant.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(req.getRole())
                .shopName(req.getShopName())
                .street(req.getStreet())
                .apt(req.getApt())
                .city(req.getCity())
                .state(req.getState())
                .zipCode(req.getZipCode())
                .status("ACTIVE")
                .totalSpent(0)
                .approved(!isFarmer)
                .build();

        if (emailVerificationEnabled) {
            String verificationToken = UUID.randomUUID().toString();
            participant.setEmailVerified(false);
            participant.setVerificationToken(verificationToken);
            participant = participantRepo.save(participant);
            emailService.sendEmailVerification(participant.getEmail(), participant.getFirstName(), verificationToken);
            if (req.getReferralCode() != null && !req.getReferralCode().isBlank()) {
                referralService.recordReferral(req.getReferralCode(), participant.getId());
            }
            return buildResponse(participant, null);
        }
        participant.setEmailVerified(true);
        participant = participantRepo.save(participant);
        if (req.getReferralCode() != null && !req.getReferralCode().isBlank()) {
            referralService.recordReferral(req.getReferralCode(), participant.getId());
        }
        return buildResponse(participant, null);
    }

    public AuthResponse login(LoginRequest req) {
        Participant participant = participantRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        if (!passwordEncoder.matches(req.getPassword(), participant.getPassword()))
            throw new AppException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");

        if (emailVerificationEnabled && !participant.isEmailVerified())
            throw new AppException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");

        String token   = jwtUtil.generateToken(participant.getId(), participant.getRole().name());
        String refresh = java.util.UUID.randomUUID().toString();
        participant.setRefreshToken(refresh);
        participant.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        participantRepo.save(participant);
        return buildResponse(participant, token, refresh);
    }

    @Transactional
    public void verifyEmail(String token) {
        Participant p = participantRepo.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification link."));
        p.setEmailVerified(true);
        p.setVerificationToken(null);
        participantRepo.save(p);
    }

    public AuthResponse getMe(String participantId) {
        Participant participant = participantRepo.findById(participantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Participant not found"));
        return buildResponse(participant, null);
    }

    @Transactional
    public AuthResponse updateProfile(String participantId, UpdateProfileRequest req) {
        Participant participant = participantRepo.findById(participantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Participant not found"));

        if (req.getFirstName() != null) participant.setFirstName(req.getFirstName());
        if (req.getLastName()  != null) participant.setLastName(req.getLastName());
        if (req.getShopName()  != null) participant.setShopName(req.getShopName());
        if (req.getStreet()    != null) participant.setStreet(req.getStreet());
        if (req.getApt()       != null) participant.setApt(req.getApt());
        if (req.getCity()      != null) participant.setCity(req.getCity());
        if (req.getState()     != null) participant.setState(req.getState());
        if (req.getZipCode()   != null) participant.setZipCode(req.getZipCode());
        if (req.getPhone()     != null) participant.setPhone(req.getPhone());
        if (req.getBio()       != null) participant.setBio(req.getBio());
        if (req.getCertifications() != null) participant.setCertifications(req.getCertifications());

        participant = participantRepo.save(participant);
        return buildResponse(participant, null);
    }

    @Transactional
    public void resendVerification(String email, EmailService emailService) {
        participantRepo.findByEmail(email).ifPresent(p -> {
            if (p.isEmailVerified()) return;
            String token = UUID.randomUUID().toString();
            p.setVerificationToken(token);
            participantRepo.save(p);
            emailService.sendEmailVerification(p.getEmail(), p.getFirstName(), token);
        });
    }

    @Transactional
    public void forgotPassword(String email, EmailService emailService) {
        participantRepo.findByEmail(email).ifPresent(p -> {
            String token = UUID.randomUUID().toString();
            p.setResetToken(token);
            p.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            participantRepo.save(p);
            emailService.sendPasswordReset(p.getEmail(), p.getFirstName(), token);
        });
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        Participant p = participantRepo.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));
        if (p.getRefreshTokenExpiry() == null || p.getRefreshTokenExpiry().isBefore(LocalDateTime.now()))
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired.");
        // Rotate: invalidate the old token immediately
        String newAccess   = jwtUtil.generateToken(p.getId(), p.getRole().name());
        String newRefresh  = java.util.UUID.randomUUID().toString();
        p.setRefreshToken(newRefresh);
        p.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        participantRepo.save(p);
        return buildResponse(p, newAccess, newRefresh);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Participant p = participantRepo.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link."));
        if (p.getResetTokenExpiry() == null || p.getResetTokenExpiry().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Reset link has expired.");
        p.setPassword(passwordEncoder.encode(newPassword));
        p.setResetToken(null);
        p.setResetTokenExpiry(null);
        participantRepo.save(p);
    }

    private AuthResponse buildResponse(Participant p, String token) {
        return buildResponse(p, token, null);
    }

    private AuthResponse buildResponse(Participant p, String token, String refreshToken) {
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .id(p.getId())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .email(p.getEmail())
                .role(p.getRole())
                .shopName(p.getShopName())
                .street(p.getStreet())
                .apt(p.getApt())
                .city(p.getCity())
                .state(p.getState())
                .zipCode(p.getZipCode())
                .approved(p.isApproved())
                .notificationPreference(p.getNotificationPreference())
                .emailPreference(p.getEmailPreference())
                .bio(p.getBio())
                .certifications(p.getCertifications())
                .build();
    }
}
