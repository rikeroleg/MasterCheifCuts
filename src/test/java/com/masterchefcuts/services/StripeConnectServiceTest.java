package com.masterchefcuts.services;

import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.LoginLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StripeConnectServiceTest {

    @Mock private ParticipantRepo participantRepo;

    @InjectMocks private StripeConnectService stripeConnectService;

    private Participant farmerNoAccount;
    private Participant farmerWithAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stripeConnectService, "stripeSecretKey", "sk_test_dummy");
        ReflectionTestUtils.setField(stripeConnectService, "returnUrl", "https://app.test/connect/return");
        ReflectionTestUtils.setField(stripeConnectService, "refreshUrl", "https://app.test/connect/refresh");

        farmerNoAccount = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true)
                .stripeAccountId(null).stripeOnboardingComplete(false).build();

        farmerWithAccount = Participant.builder()
                .id("farmer-2").firstName("Bob").lastName("Ranch")
                .role(Role.FARMER).email("bob@ranch.com").password("pass")
                .street("2 Ranch Rd").city("Rural").state("TX").zipCode("54321")
                .status("ACTIVE").approved(true)
                .stripeAccountId("acct_existing").stripeOnboardingComplete(true).build();
    }

    // ── createOnboardingLink ──────────────────────────────────────────────────

    @Test
    void createOnboardingLink_farmerNotFound_throwsIllegalArgument() {
        when(participantRepo.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeConnectService.createOnboardingLink("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Farmer not found");
    }

    @Test
    void createOnboardingLink_newFarmer_createsAccountAndReturnsUrl() throws StripeException {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmerNoAccount));
        when(participantRepo.save(any())).thenReturn(farmerNoAccount);

        Account mockAccount = mock(Account.class);
        when(mockAccount.getId()).thenReturn("acct_new");

        AccountLink mockLink = mock(AccountLink.class);
        when(mockLink.getUrl()).thenReturn("https://connect.stripe.com/onboard");

        try (MockedStatic<Account> mockedAccount = mockStatic(Account.class);
             MockedStatic<AccountLink> mockedLink = mockStatic(AccountLink.class)) {

            mockedAccount.when(() -> Account.create(any(AccountCreateParams.class))).thenReturn(mockAccount);
            mockedLink.when(() -> AccountLink.create(any(AccountLinkCreateParams.class))).thenReturn(mockLink);

            String url = stripeConnectService.createOnboardingLink("farmer-1");

            assertThat(url).isEqualTo("https://connect.stripe.com/onboard");
            assertThat(farmerNoAccount.getStripeAccountId()).isEqualTo("acct_new");
            verify(participantRepo).save(farmerNoAccount);
        }
    }

    @Test
    void createOnboardingLink_existingAccount_skipsCreateAndReturnsUrl() throws StripeException {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(farmerWithAccount));

        AccountLink mockLink = mock(AccountLink.class);
        when(mockLink.getUrl()).thenReturn("https://connect.stripe.com/refresh");

        try (MockedStatic<Account> mockedAccount = mockStatic(Account.class);
             MockedStatic<AccountLink> mockedLink = mockStatic(AccountLink.class)) {

            mockedLink.when(() -> AccountLink.create(any(AccountLinkCreateParams.class))).thenReturn(mockLink);

            String url = stripeConnectService.createOnboardingLink("farmer-2");

            assertThat(url).isEqualTo("https://connect.stripe.com/refresh");
            mockedAccount.verify(() -> Account.create(any(AccountCreateParams.class)), never());
        }
    }

    // ── createDashboardLink ───────────────────────────────────────────────────

    @Test
    void createDashboardLink_farmerNotFound_throwsIllegalArgument() {
        when(participantRepo.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeConnectService.createDashboardLink("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Farmer not found");
    }

    @Test
    void createDashboardLink_notOnboarded_throwsIllegalArgument() {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmerNoAccount));

        assertThatThrownBy(() -> stripeConnectService.createDashboardLink("farmer-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete onboarding first");
    }

    @Test
    void createDashboardLink_onboardedFarmer_returnsUrl() throws StripeException {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(farmerWithAccount));

        Account mockAccount = mock(Account.class);
        LoginLink mockLogin = mock(LoginLink.class);
        when(mockLogin.getUrl()).thenReturn("https://express.stripe.com/dashboard");

        try (MockedStatic<Account> mockedAccount = mockStatic(Account.class);
             MockedStatic<LoginLink> mockedLogin = mockStatic(LoginLink.class)) {

            mockedAccount.when(() -> Account.retrieve("acct_existing")).thenReturn(mockAccount);
            mockedLogin.when(() -> LoginLink.createOnAccount("acct_existing")).thenReturn(mockLogin);

            String url = stripeConnectService.createDashboardLink("farmer-2");

            assertThat(url).isEqualTo("https://express.stripe.com/dashboard");
        }
    }

    // ── handleAccountUpdated ──────────────────────────────────────────────────

    @Test
    void handleAccountUpdated_unknownAccount_logsWarnAndDoesNothing() {
        when(participantRepo.findByStripeAccountId("acct_unknown")).thenReturn(Optional.empty());

        // Should not throw
        assertThatCode(() -> stripeConnectService.handleAccountUpdated("acct_unknown"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleAccountUpdated_alreadyComplete_doesNotSaveAgain() throws StripeException {
        farmerWithAccount.setStripeOnboardingComplete(true);
        when(participantRepo.findByStripeAccountId("acct_existing"))
                .thenReturn(Optional.of(farmerWithAccount));

        Account mockAccount = mock(Account.class);
        when(mockAccount.getChargesEnabled()).thenReturn(true);
        when(mockAccount.getPayoutsEnabled()).thenReturn(true);

        try (MockedStatic<Account> mockedAccount = mockStatic(Account.class)) {
            mockedAccount.when(() -> Account.retrieve("acct_existing")).thenReturn(mockAccount);

            stripeConnectService.handleAccountUpdated("acct_existing");

            verify(participantRepo, never()).save(any());
        }
    }

    @Test
    void handleAccountUpdated_chargesAndPayoutsEnabled_marksOnboardingComplete() throws StripeException {
        farmerNoAccount.setStripeAccountId("acct_new");
        when(participantRepo.findByStripeAccountId("acct_new"))
                .thenReturn(Optional.of(farmerNoAccount));

        Account mockAccount = mock(Account.class);
        when(mockAccount.getChargesEnabled()).thenReturn(true);
        when(mockAccount.getPayoutsEnabled()).thenReturn(true);

        try (MockedStatic<Account> mockedAccount = mockStatic(Account.class)) {
            mockedAccount.when(() -> Account.retrieve("acct_new")).thenReturn(mockAccount);

            stripeConnectService.handleAccountUpdated("acct_new");

            assertThat(farmerNoAccount.getStripeOnboardingComplete()).isTrue();
            verify(participantRepo).save(farmerNoAccount);
        }
    }
}
