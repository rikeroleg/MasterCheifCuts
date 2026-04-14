package com.masterchefcuts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private NotificationService notificationService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AuditService auditService;
    @Mock private EmailService emailService;

    @InjectMocks private OrderService orderService;

    private Participant farmer;
    private Participant buyer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@buyer.com").password("pass")
                .street("2 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .postedAt(LocalDateTime.now()).build();
    }

    private Order buildOrder(String id, String status) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(status);
        order.setParticipantId("buyer-1");
        order.setItems("[{\"listingId\":1}]");
        order.setTotalAmount(250.00);
        return order;
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    void updateOrderStatus_accepted_sendsAcceptedEmailToBuyer() {
        Order order = buildOrder("order-aabbccdd-1234", "PAID");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "ACCEPTED");

        verify(emailService).sendOrderAccepted(order, buyer);
    }

    @Test
    void updateOrderStatus_ready_sendsReadyEmailToBuyer() {
        Order order = buildOrder("order-aabbccdd-1234", "ACCEPTED");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        // Need to get past ACCEPTED→PROCESSING→READY chain: just set status directly
        order.setStatus("PROCESSING");
        orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "READY");

        verify(emailService).sendOrderReady(order, buyer);
    }

    @Test
    void updateOrderStatus_processing_doesNotSendEmail() {
        Order order = buildOrder("order-aabbccdd-1234", "ACCEPTED");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "PROCESSING");

        verify(emailService, never()).sendOrderAccepted(any(), any());
        verify(emailService, never()).sendOrderReady(any(), any());
    }

    @Test
    void updateOrderStatus_invalidTransition_throwsException() {
        Order order = buildOrder("order-aabbccdd-1234", "PAID");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() ->
                orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "COMPLETED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void updateOrderStatus_farmerDoesNotOwnOrder_throwsException() {
        Order order = buildOrder("order-aabbccdd-1234", "PAID");
        Listing otherListing = Listing.builder()
                .id(1L).farmer(buyer)  // buyer is farmer — mismatch
                .breed("Hereford").weightLbs(400).pricePerLb(9.0).zipCode("12345")
                .postedAt(LocalDateTime.now()).build();

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(otherListing));

        assertThatThrownBy(() ->
                orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "ACCEPTED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the seller");
    }

    // ── confirmReceipt ────────────────────────────────────────────────────────

    @Test
    void confirmReceipt_sendsCompletedEmailToBuyer() {
        Order order = new Order();
        order.setId("order-aabbccdd-1234");
        order.setStatus("READY");
        order.setParticipantId("buyer-1");
        order.setItems("[]");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        orderService.confirmReceipt("buyer-1", "order-aabbccdd-1234");

        verify(emailService).sendOrderCompleted(order, buyer);
    }

    @Test
    void confirmReceipt_notReadyStatus_throwsException() {
        Order order = new Order();
        order.setId("order-aabbccdd-1234");
        order.setStatus("ACCEPTED");
        order.setParticipantId("buyer-1");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmReceipt("buyer-1", "order-aabbccdd-1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("READY status");
    }

    @Test
    void confirmReceipt_wrongBuyer_throwsException() {
        Order order = new Order();
        order.setId("order-aabbccdd-1234");
        order.setStatus("READY");
        order.setParticipantId("other-buyer");

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmReceipt("buyer-1", "order-aabbccdd-1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not your order");
    }
}
