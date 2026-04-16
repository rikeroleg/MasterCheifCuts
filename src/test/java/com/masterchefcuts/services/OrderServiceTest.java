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
import java.util.List;
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

    // â”€â”€ updateOrderStatus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

        // Need to get past ACCEPTEDâ†’PROCESSINGâ†’READY chain: just set status directly
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
                .id(1L).farmer(buyer)  // buyer is farmer â€” mismatch
                .breed("Hereford").weightLbs(400).pricePerLb(9.0).zipCode("12345")
                .postedAt(LocalDateTime.now()).build();

        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(otherListing));

        assertThatThrownBy(() ->
                orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "ACCEPTED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not the seller");
    }

    // â”€â”€ confirmReceipt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ getOrderById â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void getOrderById_buyer_returnsOrder() {
        Order order = buildOrder("order-aabb-001", "PAID");
        when(orderRepository.findById("order-aabb-001")).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById("order-aabb-001", "buyer-1");

        assertThat(result.getId()).isEqualTo("order-aabb-001");
    }

    @Test
    void getOrderById_farmerOwnsListing_returnsOrder() {
        Order order = buildOrder("order-aabb-002", "PAID");
        when(orderRepository.findById("order-aabb-002")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        Order result = orderService.getOrderById("order-aabb-002", "farmer-1");

        assertThat(result.getId()).isEqualTo("order-aabb-002");
    }

    @Test
    void getOrderById_notFound_throws() {
        when(orderRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById("bad-id", "buyer-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void getOrderById_noAccess_throwsSecurityException() {
        Order order = buildOrder("order-aabb-003", "PAID");
        when(orderRepository.findById("order-aabb-003")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing)); // listing owned by "farmer-1"

        assertThatThrownBy(() -> orderService.getOrderById("order-aabb-003", "other-farmer"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");
    }

    // â”€â”€ getFarmerOrders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void getFarmerOrders_returnsOnlyOwnedOrders() {
        Order o1 = buildOrder("order-paid-001", "PAID");    // farmer-1 owns listing 1
        Order o2 = buildOrder("order-paid-002", "ACCEPTED"); // farmer-1 owns listing 1
        Order o3 = new Order(); o3.setId("other-ord-1"); o3.setStatus("PAID");
        o3.setItems("[{\"listingId\":99}]");                 // some other listing
        when(orderRepository.findAll()).thenReturn(List.of(o1, o2, o3));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(listingRepository.findById(99L)).thenReturn(Optional.empty());

        List<Order> result = orderService.getFarmerOrders("farmer-1");

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Order::getId)).containsExactlyInAnyOrder("order-paid-001", "order-paid-002");
    }

    @Test
    void getFarmerOrders_excludesPendingPaymentOrders() {
        Order o = buildOrder("order-pend-01", "PENDING_PAYMENT");
        when(orderRepository.findAll()).thenReturn(List.of(o));

        List<Order> result = orderService.getFarmerOrders("farmer-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getFarmerOrders_sortsByStatusPriority() {
        Order oAccepted = new Order(); oAccepted.setId("order-acc-0001"); oAccepted.setStatus("ACCEPTED");
        oAccepted.setItems("[{\"listingId\":1}]"); oAccepted.setOrderDate("2026-01-20T10:00:00");
        Order oPaid = new Order(); oPaid.setId("order-paid-001x"); oPaid.setStatus("PAID");
        oPaid.setItems("[{\"listingId\":1}]"); oPaid.setOrderDate("2026-01-15T10:00:00");

        when(orderRepository.findAll()).thenReturn(List.of(oAccepted, oPaid));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        List<Order> result = orderService.getFarmerOrders("farmer-1");

        assertThat(result.get(0).getId()).isEqualTo("order-paid-001x"); // PAID = priority 1
        assertThat(result.get(1).getId()).isEqualTo("order-acc-0001");  // ACCEPTED = priority 2
    }

    // â”€â”€ updateOrderStatus â€” invalid transitions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void updateOrderStatus_invalidStatusString_throws() {
        Order order = buildOrder("order-aabbccdd-1234", "PAID");
        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void updateOrderStatus_invalidTransition_throws() {
        Order order = buildOrder("order-aabbccdd-1234", "ACCEPTED");
        when(orderRepository.findById("order-aabbccdd-1234")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> orderService.updateOrderStatus("farmer-1", "order-aabbccdd-1234", "COMPLETED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void updateOrderStatus_readyToCompleted_sendsCompletedNotification() {
        Order order = buildOrder("order-aabbccdd-9999", "READY");
        order.setOrderDate(java.time.LocalDateTime.now().toString());

        when(orderRepository.findById("order-aabbccdd-9999")).thenReturn(Optional.of(order));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(orderRepository.save(any())).thenReturn(order);

        Order result = orderService.updateOrderStatus("farmer-1", "order-aabbccdd-9999", "COMPLETED");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(notificationService).send(eq(buyer), any(), anyString(), anyString(), anyString(), any(), anyString());
    }

    // â”€â”€ notifyFarmerOfCompletion (via confirmReceipt with listing) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void confirmReceipt_withValidItems_notifiesFarmer() {
        Order order = new Order();
        order.setId("order-aabbccdd-9000");
        order.setStatus("READY");
        order.setParticipantId("buyer-1");
        order.setItems("[{\"listingId\":1}]");

        when(orderRepository.findById("order-aabbccdd-9000")).thenReturn(Optional.of(order));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.confirmReceipt("buyer-1", "order-aabbccdd-9000");

        verify(notificationService).send(eq(farmer), any(), anyString(), anyString(), anyString(), eq(1L), anyString());
    }
}
