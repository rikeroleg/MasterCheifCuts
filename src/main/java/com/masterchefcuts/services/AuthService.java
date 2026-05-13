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
import lombok.extern.slf4j.Slf4j;
import com.masterchefcuts.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";

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
                // Resend verification but never return existing user data — that would
                // enable email enumeration and expose internal profile fields.
                String newToken = UUID.randomUUID().toString();
                ex.setVerificationToken(sha256(newToken));
                ex.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
                participantRepo.save(ex);
                emailService.sendEmailVerification(ex.getEmail(), ex.getFirstName(), newToken);
                log.info("register: verification re-sent for unverified account [{}]", maskEmail(req.getEmail()));
                throw new AppException(HttpStatus.CONFLICT, "EMAIL_NOT_VERIFIED");
            }
            log.warn("register: duplicate email attempt [{}]", maskEmail(req.getEmail()));
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
                .status(STATUS_ACTIVE)
                .totalSpent(0)
                .approved(!isFarmer)
                .build();

        if (emailVerificationEnabled) {
            String rawVerificationToken = UUID.randomUUID().toString();
            participant.setEmailVerified(false);
            participant.setVerificationToken(sha256(rawVerificationToken));
            participant.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            participant = participantRepo.save(participant);
            emailService.sendEmailVerification(participant.getEmail(), participant.getFirstName(), rawVerificationToken);
            if (req.getReferralCode() != null && !req.getReferralCode().isBlank()) {
                referralService.recordReferral(req.getReferralCode(), participant.getId());
            }
            log.info("register: verification email sent [{}] role={}", maskEmail(req.getEmail()), req.getRole());
            return buildResponse(participant, null);
        }
        participant.setEmailVerified(true);
        participant = participantRepo.save(participant);

        String token = jwtUtil.generateToken(participant.getId(), participant.getRole().name());
        String refresh = java.util.UUID.randomUUID().toString();
        participant.setRefreshToken(refresh);
        participant.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        participant = participantRepo.save(participant);
        
        if (req.getReferralCode() != null && !req.getReferralCode().isBlank()) {
            referralService.recordReferral(req.getReferralCode(), participant.getId());
        }
        log.info("register: success [{}] role={}", maskEmail(req.getEmail()), req.getRole());
        return buildResponse(participant, token, refresh);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Participant participant = participantRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        if (!passwordEncoder.matches(req.getPassword(), participant.getPassword()))
            throw new AppException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");

        if (!STATUS_ACTIVE.equalsIgnoreCase(participant.getStatus()))
            throw new AppException(HttpStatus.FORBIDDEN, "Account is suspended or inactive.");

        if (emailVerificationEnabled && !participant.isEmailVerified())
            throw new AppException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");

        String token   = jwtUtil.generateToken(participant.getId(), participant.getRole().name());
        String refresh = java.util.UUID.randomUUID().toString();
        participant.setRefreshToken(refresh);
        participant.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        participantRepo.save(participant);
        log.info("login: success [{}] role={}", maskEmail(req.getEmail()), participant.getRole());
        return buildResponse(participant, token, refresh);
    }

    @Transactional
    public void verifyEmail(String token) {
        Participant p = participantRepo.findByVerificationToken(sha256(token))
                .or(() -> participantRepo.findByVerificationToken(token))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Invalid or expired verification link."));
        if (p.getVerificationTokenExpiry() != null && p.getVerificationTokenExpiry().isBefore(LocalDateTime.now()))
            throw new AppException(HttpStatus.BAD_REQUEST, "Verification link has expired. Please request a new one.");
        if (token.equals(p.getVerificationToken())) {
            p.setVerificationToken(sha256(token));
        }
        p.setEmailVerified(true);
        p.setVerificationToken(null);
        p.setVerificationTokenExpiry(null);
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
            String rawToken = UUID.randomUUID().toString();
            p.setVerificationToken(sha256(rawToken));
            p.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            participantRepo.save(p);
            emailService.sendEmailVerification(p.getEmail(), p.getFirstName(), rawToken);
        });
    }

    @Transactional
    public void forgotPassword(String email, EmailService emailService) {
        participantRepo.findByEmail(email).ifPresent(p -> {
            String rawToken = UUID.randomUUID().toString();
            p.setResetToken(sha256(rawToken));
            p.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            participantRepo.save(p);
            emailService.sendPasswordReset(p.getEmail(), p.getFirstName(), rawToken);
        });
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        Participant p = participantRepo.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));
        if (p.getRefreshTokenExpiry() == null || p.getRefreshTokenExpiry().isBefore(LocalDateTime.now()))
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired.");
        if (!STATUS_ACTIVE.equalsIgnoreCase(p.getStatus()))
            throw new AppException(HttpStatus.UNAUTHORIZED, "Account is suspended or inactive.");
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
        Participant p = participantRepo.findByResetToken(sha256(token))
                .or(() -> participantRepo.findByResetToken(token))
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link."));
        if (p.getResetTokenExpiry() == null || p.getResetTokenExpiry().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Reset link has expired.");
        if (token.equals(p.getResetToken())) {
            p.setResetToken(sha256(token));
        }
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
                .tokenExpiresAt(token != null ? System.currentTimeMillis() + jwtUtil.getExpirationMs() : null)
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

    /**
     * SHA-256 hash a token value before persisting. Raw tokens are sent via email;
     * only their hash is stored so a DB breach cannot be used to reset accounts.
     */
    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Mask email for safe logging — e.g. jo***@example.com */
    private static String maskEmail(String email) {
        if (email == null) return "null";
        String normalized = email.trim();
        if (normalized.isEmpty()) return "***";
        int at = normalized.indexOf('@');
        if (at <= 0) return "***";
        if (at <= 2) return "***" + normalized.substring(at);
        return normalized.substring(0, 2) + "***" + normalized.substring(at);
    }
}
