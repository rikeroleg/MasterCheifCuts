package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @InjectMocks EmailService emailService;

    private Participant buyer;
    private Participant farmer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "no-reply@test.com");

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
    void sendEmailVerification_sends1Email() {
        emailService.sendEmailVerification("bob@buyer.com", "Bob", "verify-token");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPasswordReset_sends1Email() {
        emailService.sendPasswordReset("bob@buyer.com", "Bob", "reset-token");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendClaimConfirmation_sends1Email() {
        emailService.sendClaimConfirmation(buyer, listing, "Ribeye");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPoolFullToBuyers_sendsOneEmailPerBuyer() {
        Participant buyer2 = Participant.builder()
                .id("buyer-2").firstName("Alice").lastName("Smith")
                .email("alice@smith.com").role(Role.BUYER).street("3 St")
                .city("Town").state("TX").zipCode("12345").status("ACTIVE").approved(true).build();

        emailService.sendPoolFullToBuyers(List.of(buyer, buyer2), listing);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPoolFullToFarmer_sends1Email() {
        emailService.sendPoolFullToFarmer(farmer, listing);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendProcessingDateSet_sendsOneEmailPerBuyer() {
        emailService.sendProcessingDateSet(List.of(buyer), listing, LocalDate.now().plusDays(7));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_whenMailSenderThrows_exceptionIsSilenced() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not propagate — service catches and logs
        emailService.sendEmailVerification("bob@buyer.com", "Bob", "token");
    }

    // ── sendOrderAccepted / sendOrderReady / sendOrderCompleted ─────────────────

    @Test
    void sendOrderAccepted_sends1EmailToBuyer() {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");
        order.setTotalAmount(200.0);

        emailService.sendOrderAccepted(order, buyer);

        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo() != null && "bob@buyer.com".equals(msg.getTo()[0])));
    }

    @Test
    void sendOrderReady_sends1EmailToBuyer() {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");

        emailService.sendOrderReady(order, buyer);

        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo() != null && "bob@buyer.com".equals(msg.getTo()[0])));
    }

    @Test
    void sendOrderCompleted_sends1EmailToBuyer() {
        com.masterchefcuts.model.Order order = new com.masterchefcuts.model.Order();
        order.setId("aabbccdd-0000-0000-0000-000000000000");

        emailService.sendOrderCompleted(order, buyer);

        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo() != null && "bob@buyer.com".equals(msg.getTo()[0])));
    }

    // ── sendFarmerApproved ────────────────────────────────────────────────────

    @Test
    void sendFarmerApproved_sends1Email() {
        emailService.sendFarmerApproved(farmer);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendFarmerApproved_emailAddressedToFarmer() {
        emailService.sendFarmerApproved(farmer);

        verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                msg.getTo() != null &&
                msg.getTo().length == 1 &&
                "jane@farm.com".equals(msg.getTo()[0])
        ));
    }
}

