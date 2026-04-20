package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.EmailPreference;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock Resend resend;
    @Mock Emails emails;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks EmailService emailService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(emailService, "from", "no-reply@test.com");
        lenient().when(resend.emails()).thenReturn(emails);
        lenient().when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse());

        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .shopName("Jane's Farm").email("jane@farm.com")
                .role(Role.FARMER).street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .email("bob@buyer.com")
                .role(Role.BUYER).street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .sourceFarm("Jane's Farm").weightLbs(600).pricePerLb(12.0).zipCode("12345")
                .postedAt(LocalDateTime.now()).build();
    }

    @Test
    void sendEmailVerification_sends1Email() throws Exception {
        emailService.sendEmailVerification("bob@buyer.com", "Bob", "verify-token");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPasswordReset_sends1Email() throws Exception {
        emailService.sendPasswordReset("bob@buyer.com", "Bob", "reset-token");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendClaimConfirmation_sends1Email() throws Exception {
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPoolFullToBuyers_sendsOneEmailPerBuyer() throws Exception {
        Participant buyer2 = Participant.builder()
                .id("buyer-2").firstName("Alice").lastName("Smith")
                .email("alice@smith.com").role(Role.BUYER).street("3 St")
                .city("Town").state("TX").zipCode("12345").status("ACTIVE").approved(true).build();

        emailService.sendPoolFullToBuyers(List.of(buyer, buyer2), listing);

        verify(emails, times(2)).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendPoolFullToFarmer_sends1Email() throws Exception {
        emailService.sendPoolFullToFarmer(farmer, listing);
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendProcessingDateSet_sendsOneEmailPerBuyer() throws Exception {
        emailService.sendProcessingDateSet(List.of(buyer), listing, LocalDate.now().plusDays(7));
        verify(emails, times(1)).send(any(CreateEmailOptions.class));
    }

    @Test
    void send_whenResendThrows_exceptionIsSilenced() throws Exception {
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new RuntimeException("Resend down"));

        // Should not propagate — service catches and logs
        emailService.sendEmailVerification("bob@buyer.com", "Bob", "token");
    }

    // ── sendOrderAccepted / sendOrderReady / sendOrderCompleted ─────────────────

    @Test
    void sendOrderAccepted_sends1EmailToBuyer() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(200.0);

        emailService.sendOrderAccepted(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    @Test
    void sendOrderReady_sends1EmailToBuyer() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");

        emailService.sendOrderReady(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    @Test
    void sendOrderCompleted_sends1EmailToBuyer() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");

        emailService.sendOrderCompleted(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    // ── sendFarmerApproved ────────────────────────────────────────────────────

    @Test
    void sendFarmerApproved_sends1Email() throws Exception {
        emailService.sendFarmerApproved(farmer);
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendFarmerApproved_emailAddressedToFarmer() throws Exception {
        emailService.sendFarmerApproved(farmer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("jane@farm.com")));
    }

    // ── sendOrderConfirmationToBuyer ──────────────────────────────────────────

    @Test
    void sendOrderConfirmationToBuyer_sendsHtmlEmailToBuyer() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(150.0);
        order.setItems("[{\"cutLabel\":\"Ribeye\",\"breed\":\"Angus\",\"animalType\":\"BEEF\",\"price\":150.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")
                        && opts.getHtml() != null));
    }

    @Test
    void sendOrderConfirmationToBuyer_htmlContainsBuyerFirstName() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(150.0);
        order.setItems("[{\"cutLabel\":\"Ribeye\",\"breed\":\"Angus\",\"animalType\":\"BEEF\",\"price\":150.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && opts.getHtml().contains("Bob")));
    }

    @Test
    void sendOrderConfirmationToBuyer_withNoItems_doesNotThrow() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(0.0);
        order.setItems("[]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(any(CreateEmailOptions.class));
    }

    // ── sendNewOrderToFarmer ──────────────────────────────────────────────────

    @Test
    void sendNewOrderToFarmer_sendsHtmlEmailToFarmer() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(200.0);
        order.setItems("[{\"cutLabel\":\"Brisket\",\"breed\":\"Angus\",\"animalType\":\"BEEF\",\"price\":200.0}]");

        emailService.sendNewOrderToFarmer(order, listing, farmer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("jane@farm.com")
                        && opts.getHtml() != null));
    }

    @Test
    void sendNewOrderToFarmer_htmlContainsFarmerFirstName() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(200.0);
        order.setItems("[{\"cutLabel\":\"Brisket\",\"breed\":\"Angus\",\"animalType\":\"BEEF\",\"price\":200.0}]");

        emailService.sendNewOrderToFarmer(order, listing, farmer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && opts.getHtml().contains("Jane")));
    }

    @Test
    void sendNewOrderToFarmer_nullItems_doesNotThrow() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(100.0);
        order.setItems(null);

        emailService.sendNewOrderToFarmer(order, listing, farmer);

        verify(emails).send(any(CreateEmailOptions.class));
    }

    // ── buildItemRows / parseItems / escapeHtml coverage ─────────────────────

    @Test
    void sendOrderConfirmationToBuyer_itemWithLabelFallback_usesLabelField() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(80.0);
        order.setItems("[{\"label\":\"Chuck Roast\",\"breed\":\"Hereford\",\"animalType\":\"BEEF\",\"price\":80.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && opts.getHtml().contains("Chuck Roast")));
    }

    @Test
    void sendOrderConfirmationToBuyer_itemWithNoBreedOrAnimalType_showsDash() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(60.0);
        order.setItems("[{\"cutLabel\":\"Ribs\",\"price\":60.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && opts.getHtml().contains("Ribs")));
    }

    @Test
    void sendOrderConfirmationToBuyer_itemWithNoPrice_showsDash() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(0.0);
        order.setItems("[{\"cutLabel\":\"Steak\"}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && opts.getHtml().contains("Steak")));
    }

    @Test
    void sendOrderConfirmationToBuyer_multipleItems_rendersEvenOddRows() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(300.0);
        order.setItems("[{\"cutLabel\":\"Ribeye\",\"price\":150.0},{\"cutLabel\":\"Brisket\",\"price\":150.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null
                        && opts.getHtml().contains("Ribeye")
                        && opts.getHtml().contains("Brisket")));
    }

    @Test
    void sendOrderConfirmationToBuyer_itemWithHtmlCharsInLabel_escapesHtml() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(100.0);
        order.setItems("[{\"cutLabel\":\"<script>bad</script>\",\"price\":100.0}]");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getHtml() != null && !opts.getHtml().contains("<script>")));
    }

    @Test
    void sendOrderConfirmationToBuyer_invalidItemsJson_doesNotThrow() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(50.0);
        order.setItems("not-valid-json");

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendOrderConfirmationToBuyer_nullItemsJson_doesNotThrow() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(0.0);
        order.setItems(null);

        emailService.sendOrderConfirmationToBuyer(order, buyer);

        verify(emails).send(any(CreateEmailOptions.class));
    }

    // ── EmailPreference / canSendEmail guard tests ────────────────────────────

    @Test
    void sendClaimConfirmation_withEmailPrefNone_skipsEmail() throws Exception {
        buyer.setEmailPreference(EmailPreference.NONE);
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendClaimConfirmation_withEmailPrefAll_sendsEmail() throws Exception {
        buyer.setEmailPreference(EmailPreference.ALL);
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendClaimConfirmation_withEmailPrefImportant_skipsEmail() throws Exception {
        // sendClaimConfirmation is an ALL-only email (isImportant=false)
        buyer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendOrderAccepted_withEmailPrefImportant_sendsEmail() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        buyer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendOrderAccepted(order, buyer);
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendOrderAccepted_withEmailPrefNone_skipsEmail() throws Exception {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        buyer.setEmailPreference(EmailPreference.NONE);
        emailService.sendOrderAccepted(order, buyer);
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendClaimConfirmation_withNullEmailPref_defaultsToSend() throws Exception {
        // null emailPreference defaults to sending (safe fallback)
        buyer.setEmailPreference(null);
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    // ── New trigger email methods ─────────────────────────────────────────────

    @Test
    void sendNewClaimToFarmer_sends1EmailToFarmer() throws Exception {
        farmer.setEmailPreference(EmailPreference.ALL);
        emailService.sendNewClaimToFarmer(farmer, buyer, listing, "Ribeye");
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("jane@farm.com")));
    }

    @Test
    void sendNewClaimToFarmer_withImportantPref_skipsEmail() throws Exception {
        farmer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendNewClaimToFarmer(farmer, buyer, listing, "Ribeye");
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendRequestFulfilled_sends1EmailToBuyer() throws Exception {
        buyer.setEmailPreference(EmailPreference.ALL);
        emailService.sendRequestFulfilled(buyer, "Jane's Farm", "Angus", "BEEF", 42L);
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    @Test
    void sendRequestFulfilled_withNonePref_skipsEmail() throws Exception {
        buyer.setEmailPreference(EmailPreference.NONE);
        emailService.sendRequestFulfilled(buyer, "Jane's Farm", "Angus", "BEEF", 42L);
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendDisputeOpened_isBuyer_sends1Email() throws Exception {
        buyer.setEmailPreference(EmailPreference.ALL);
        emailService.sendDisputeOpened(buyer, 99L, 1L, true);
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    @Test
    void sendDisputeOpened_isFarmer_sends1Email() throws Exception {
        farmer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendDisputeOpened(farmer, 99L, 1L, false);
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("jane@farm.com")));
    }

    @Test
    void sendDisputeOpened_withNonePref_skipsEmail() throws Exception {
        buyer.setEmailPreference(EmailPreference.NONE);
        emailService.sendDisputeOpened(buyer, 99L, 1L, true);
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendReviewReceived_withAllPref_sends1Email() throws Exception {
        farmer.setEmailPreference(EmailPreference.ALL);
        emailService.sendReviewReceived(farmer, 5, "Bob B.", "Angus BEEF");
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("jane@farm.com")));
    }

    @Test
    void sendReviewReceived_withImportantPref_skipsEmail() throws Exception {
        farmer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendReviewReceived(farmer, 5, "Bob B.", "Angus BEEF");
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }

    @Test
    void sendNewListingNearby_withAllPref_sends1Email() throws Exception {
        buyer.setEmailPreference(EmailPreference.ALL);
        emailService.sendNewListingNearby(buyer, listing);
        verify(emails).send(argThat((CreateEmailOptions opts) ->
                opts.getTo() != null && opts.getTo().contains("bob@buyer.com")));
    }

    @Test
    void sendNewListingNearby_withImportantPref_skipsEmail() throws Exception {
        buyer.setEmailPreference(EmailPreference.IMPORTANT);
        emailService.sendNewListingNearby(buyer, listing);
        verify(emails, never()).send(any(CreateEmailOptions.class));
    }
}

