package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    @InjectMocks EmailService emailService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(emailService, "from", "no-reply@test.com");
        when(resend.emails()).thenReturn(emails);
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
}

