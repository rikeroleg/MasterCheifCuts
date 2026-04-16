package com.masterchefcuts.services;

import com.masterchefcuts.config.JwtUtil;
import com.masterchefcuts.dto.AuthResponse;
import com.masterchefcuts.dto.LoginRequest;
import com.masterchefcuts.dto.RegisterRequest;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private ParticipantRepo participantRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;
    @Mock private ReferralService referralService;

    @InjectMocks private AuthService authService;

    private Participant participant;

    @BeforeEach
    void setUp() {
        participant = Participant.builder()
                .id("user-1")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("encoded-pass")
                .role(Role.BUYER)
                .street("123 Main St")
                .city("Springfield")
                .state("IL")
                .zipCode("62701")
                .status("ACTIVE")
                .approved(true)
                .emailVerified(true)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_newBuyerSaved() {
        RegisterRequest req = buildRegisterRequest(Role.BUYER);
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(req.getPassword())).thenReturn("encoded-pass");
        when(participantRepo.save(any(Participant.class))).thenReturn(participant);

        AuthResponse response = authService.register(req, emailService);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("user-1");
        verify(participantRepo).save(any(Participant.class));
    }

    @Test
    void register_farmerApprovedFalseByDefault() {
        RegisterRequest req = buildRegisterRequest(Role.FARMER);
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-pass");
        Participant farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .email("jane@farm.com").password("encoded-pass")
                .role(Role.FARMER).street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(false).emailVerified(true).build();
        when(participantRepo.save(any(Participant.class))).thenReturn(farmer);

        AuthResponse response = authService.register(req, emailService);

        assertThat(response.isApproved()).isFalse();
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        RegisterRequest req = buildRegisterRequest(Role.BUYER);
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> authService.register(req, emailService))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsToken() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password");

        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(participant));
        when(passwordEncoder.matches(req.getPassword(), participant.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(participant.getId(), participant.getRole().name())).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_throwsWhenEmailNotFound() {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("password");

        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Incorrect email or password");
    }

    @Test
    void login_throwsWhenPasswordDoesNotMatch() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("wrong-pass");

        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(participant));
        when(passwordEncoder.matches(req.getPassword(), participant.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Incorrect email or password");
    }

    // ── verifyEmail ───────────────────────────────────────────────────────────

    @Test
    void verifyEmail_success_setsEmailVerifiedAndClearsToken() {
        participant.setEmailVerified(false);
        participant.setVerificationToken("valid-token");
        when(participantRepo.findByVerificationToken("valid-token")).thenReturn(Optional.of(participant));

        authService.verifyEmail("valid-token");

        assertThat(participant.isEmailVerified()).isTrue();
        assertThat(participant.getVerificationToken()).isNull();
        verify(participantRepo).save(participant);
    }

    @Test
    void verifyEmail_throwsForInvalidToken() {
        when(participantRepo.findByVerificationToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired");
    }

    // ── getMe ─────────────────────────────────────────────────────────────────

    @Test
    void getMe_success_returnsParticipantData() {
        when(participantRepo.findById("user-1")).thenReturn(Optional.of(participant));

        AuthResponse response = authService.getMe("user-1");

        assertThat(response.getId()).isEqualTo("user-1");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getMe_throwsWhenNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_updatesAllNonNullFields() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Jane");
        req.setLastName("Updated");
        req.setShopName("New Shop");
        req.setStreet("456 Oak Ave");
        req.setApt("Suite 2");
        req.setCity("Dallas");
        req.setState("TX");
        req.setZipCode("75001");
        req.setPhone("555-1234");

        when(participantRepo.findById("user-1")).thenReturn(Optional.of(participant));
        when(participantRepo.save(participant)).thenReturn(participant);

        AuthResponse response = authService.updateProfile("user-1", req);

        assertThat(participant.getFirstName()).isEqualTo("Jane");
        assertThat(participant.getLastName()).isEqualTo("Updated");
        assertThat(participant.getShopName()).isEqualTo("New Shop");
        assertThat(participant.getStreet()).isEqualTo("456 Oak Ave");
        assertThat(participant.getApt()).isEqualTo("Suite 2");
        assertThat(participant.getCity()).isEqualTo("Dallas");
        assertThat(participant.getState()).isEqualTo("TX");
        assertThat(participant.getZipCode()).isEqualTo("75001");
        assertThat(participant.getPhone()).isEqualTo("555-1234");
        assertThat(response).isNotNull();
    }

    @Test
    void updateProfile_updatesNonNullFields() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Jane");
        req.setCity("Chicago");

        when(participantRepo.findById("user-1")).thenReturn(Optional.of(participant));
        when(participantRepo.save(participant)).thenReturn(participant);

        AuthResponse response = authService.updateProfile("user-1", req);

        assertThat(participant.getFirstName()).isEqualTo("Jane");
        assertThat(participant.getCity()).isEqualTo("Chicago");
        assertThat(response).isNotNull();
    }

    @Test
    void updateProfile_throwsWhenParticipantNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.updateProfile("missing", new RegisterRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_sendsEmailWhenUserExists() {
        when(participantRepo.findByEmail("john@example.com")).thenReturn(Optional.of(participant));
        when(participantRepo.save(any(Participant.class))).thenReturn(participant);

        authService.forgotPassword("john@example.com", emailService);

        verify(emailService).sendPasswordReset(eq("john@example.com"), eq("John"), anyString());
    }

    @Test
    void forgotPassword_doesNothingWhenEmailNotFound() {
        when(participantRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@example.com", emailService);

        verify(emailService, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_success_encodesNewPassword() {
        participant.setResetToken("reset-token");
        participant.setResetTokenExpiry(LocalDateTime.now().plusHours(1));

        when(participantRepo.findByResetToken("reset-token")).thenReturn(Optional.of(participant));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new-pass");

        authService.resetPassword("reset-token", "new-pass");

        assertThat(participant.getPassword()).isEqualTo("encoded-new-pass");
        assertThat(participant.getResetToken()).isNull();
        assertThat(participant.getResetTokenExpiry()).isNull();
        verify(participantRepo).save(participant);
    }

    @Test
    void resetPassword_throwsForInvalidToken() {
        when(participantRepo.findByResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "new-pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void resetPassword_throwsWhenTokenExpired() {
        participant.setResetToken("expired-token");
        participant.setResetTokenExpiry(LocalDateTime.now().minusHours(1));

        when(participantRepo.findByResetToken("expired-token")).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "new-pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(Role role) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        req.setRole(role);
        req.setStreet("123 Main St");
        req.setCity("Springfield");
        req.setState("IL");
        req.setZipCode("62701");
        return req;
    }

    // ── register with email verification enabled ───────────────────────────────

    @Test
    void register_emailVerificationEnabled_savesUnverifiedAndSendsEmail() {
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", true);
        RegisterRequest req = buildRegisterRequest(Role.BUYER);
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-pass");
        Participant unverified = Participant.builder()
                .id("user-2").firstName("John").lastName("Doe")
                .email("john@example.com").password("encoded-pass")
                .role(Role.BUYER).status("ACTIVE").approved(true)
                .emailVerified(false).verificationToken("v-token").build();
        when(participantRepo.save(any(Participant.class))).thenReturn(unverified);

        AuthResponse response = authService.register(req, emailService);

        assertThat(response.getToken()).isNull();
        verify(emailService).sendEmailVerification(eq("john@example.com"), eq("John"), anyString());
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", false);
    }

    @Test
    void register_emailVerificationEnabled_existingUnverified_resendAndReturn() {
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", true);
        RegisterRequest req = buildRegisterRequest(Role.BUYER);
        Participant unverified = Participant.builder()
                .id("user-1").firstName("John").lastName("Doe")
                .email("john@example.com").password("encoded-pass")
                .role(Role.BUYER).status("ACTIVE").approved(true)
                .emailVerified(false).verificationToken("old-token").build();
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(unverified));
        when(participantRepo.save(unverified)).thenReturn(unverified);

        AuthResponse response = authService.register(req, emailService);

        assertThat(response.getToken()).isNull();
        verify(emailService).sendEmailVerification(eq("john@example.com"), eq("John"), anyString());
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", false);
    }

    @Test
    void register_withReferralCode_recordsReferral() {
        RegisterRequest req = buildRegisterRequest(Role.BUYER);
        req.setReferralCode("ref-abc");
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-pass");
        when(participantRepo.save(any(Participant.class))).thenReturn(participant);

        authService.register(req, emailService);

        verify(referralService).recordReferral(eq("ref-abc"), eq("user-1"));
    }

    // ── login with email verification enabled ─────────────────────────────────

    @Test
    void login_emailNotVerified_throwsEmailNotVerified() {
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", true);
        participant.setEmailVerified(false);
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password");
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(participant));
        when(passwordEncoder.matches(req.getPassword(), participant.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("EMAIL_NOT_VERIFIED");
        ReflectionTestUtils.setField(authService, "emailVerificationEnabled", false);
    }

    @Test
    void login_savesRefreshToken_returnsTokens() {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("password");
        when(participantRepo.findByEmail(req.getEmail())).thenReturn(Optional.of(participant));
        when(passwordEncoder.matches(req.getPassword(), participant.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(participant.getId(), participant.getRole().name())).thenReturn("jwt-token");
        when(participantRepo.save(any(Participant.class))).thenReturn(participant);

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(participantRepo).save(participant);
    }

    // ── resendVerification ────────────────────────────────────────────────────

    @Test
    void resendVerification_whenNotVerified_sendsEmail() {
        participant.setEmailVerified(false);
        participant.setVerificationToken("old-token");
        when(participantRepo.findByEmail("john@example.com")).thenReturn(Optional.of(participant));
        when(participantRepo.save(participant)).thenReturn(participant);

        authService.resendVerification("john@example.com", emailService);

        verify(emailService).sendEmailVerification(eq("john@example.com"), eq("John"), anyString());
    }

    @Test
    void resendVerification_whenAlreadyVerified_doesNotSendEmail() {
        participant.setEmailVerified(true);
        when(participantRepo.findByEmail("john@example.com")).thenReturn(Optional.of(participant));

        authService.resendVerification("john@example.com", emailService);

        verify(emailService, never()).sendEmailVerification(anyString(), anyString(), anyString());
    }

    @Test
    void resendVerification_whenEmailNotFound_doesNothing() {
        when(participantRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.resendVerification("unknown@example.com", emailService);

        verify(emailService, never()).sendEmailVerification(anyString(), anyString(), anyString());
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_success_returnsNewTokens() {
        participant.setRefreshToken("valid-refresh");
        participant.setRefreshTokenExpiry(LocalDateTime.now().plusDays(3));
        when(participantRepo.findByRefreshToken("valid-refresh")).thenReturn(Optional.of(participant));
        when(jwtUtil.generateToken(participant.getId(), participant.getRole().name())).thenReturn("new-access-token");
        when(participantRepo.save(any(Participant.class))).thenReturn(participant);

        AuthResponse response = authService.refreshToken("valid-refresh");

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isNotEqualTo("valid-refresh");
        verify(participantRepo).save(participant);
    }

    @Test
    void refreshToken_invalidToken_throws() {
        when(participantRepo.findByRefreshToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshToken_expiredToken_throws() {
        participant.setRefreshToken("expired-refresh");
        participant.setRefreshTokenExpiry(LocalDateTime.now().minusDays(1));
        when(participantRepo.findByRefreshToken("expired-refresh")).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> authService.refreshToken("expired-refresh"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
    }
}
