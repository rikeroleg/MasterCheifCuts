package com.masterchefcuts.services;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Comment;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.CommentRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ParticipantRepo participantRepo;
    @Mock private ListingRepository listingRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RefundService refundService;
    @Mock private AuditService auditService;
    @Mock private CommentRepository commentRepository;
    @Mock private EmailService emailService;

    @InjectMocks private AdminService adminService;

    private Participant buyer;
    private Participant farmer;
    private Participant pendingFarmer;
    private Listing listing;

    @BeforeEach
    void setUp() {
        buyer = Participant.builder()
                .id("buyer-1").firstName("Bob").lastName("Buyer")
                .role(Role.BUYER).email("bob@example.com").password("pass")
                .street("1 St").city("City").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        pendingFarmer = Participant.builder()
                .id("farmer-2").firstName("New").lastName("Farmer")
                .role(Role.FARMER).email("new@farm.com").password("pass")
                .street("2 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(false).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(500).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
    }

    // ── getAllUsers ───────────────────────────────────────────────────────────

    @Test
    void getAllUsers_returnsAllParticipants() {
        when(participantRepo.findAll()).thenReturn(List.of(buyer, farmer, pendingFarmer));

        List<Participant> result = adminService.getAllUsers();

        assertThat(result).hasSize(3);
    }

    // ── setApproved ───────────────────────────────────────────────────────────

    @Test
    void setApproved_approvesUser() {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(pendingFarmer));
        when(participantRepo.save(pendingFarmer)).thenReturn(pendingFarmer);

        Participant result = adminService.setApproved("farmer-2", true);

        assertThat(result.isApproved()).isTrue();
        verify(participantRepo).save(pendingFarmer);
    }

    @Test
    void setApproved_revokesApproval() {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(participantRepo.save(farmer)).thenReturn(farmer);

        Participant result = adminService.setApproved("farmer-1", false);

        assertThat(result.isApproved()).isFalse();
    }

    @Test
    void setApproved_throwsWhenUserNotFound() {
        when(participantRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.setApproved("missing", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ── deleteListing ─────────────────────────────────────────────────────────

    @Test
    void deleteListing_callsRepositoryDeleteById() {
        adminService.deleteListing(1L);

        verify(listingRepository).deleteById(1L);
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsAggregatedCounts() {
        when(participantRepo.count()).thenReturn(3L);
        when(listingRepository.count()).thenReturn(2L);
        when(claimRepository.count()).thenReturn(5L);
        when(participantRepo.findAll()).thenReturn(List.of(buyer, farmer, pendingFarmer));

        Map<String, Object> stats = adminService.getStats();

        assertThat(stats.get("totalUsers")).isEqualTo(3L);
        assertThat(stats.get("totalListings")).isEqualTo(2L);
        assertThat(stats.get("totalClaims")).isEqualTo(5L);
        assertThat(stats.get("pendingFarmers")).isEqualTo(1L);
    }

    @Test
    void getStats_pendingFarmersCountsOnlyUnapprovedFarmers() {
        when(participantRepo.count()).thenReturn(1L);
        when(listingRepository.count()).thenReturn(0L);
        when(claimRepository.count()).thenReturn(0L);
        when(participantRepo.findAll()).thenReturn(List.of(buyer));

        Map<String, Object> stats = adminService.getStats();

        assertThat(stats.get("pendingFarmers")).isEqualTo(0L);
    }

    // ── getCommentsPaged ──────────────────────────────────────────────

    @Test
    void getCommentsPaged_returnsMapWithContent() {
        Comment comment = Comment.builder()
                .id(1L).author(buyer).body("Nice listing!").createdAt(LocalDateTime.now()).build();
        Page<Comment> page = new PageImpl<>(List.of(comment));
        when(commentRepository.findAll(any(Pageable.class))).thenReturn(page);

        Map<String, Object> result = adminService.getCommentsPaged(0, 25);

        assertThat(result.get("totalElements")).isEqualTo(1L);
        assertThat(result.get("hasNext")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("id")).isEqualTo(1L);
    }

    @Test
    void getCommentsPaged_emptyPage_returnsEmptyContent() {
        Page<Comment> empty = new PageImpl<>(List.of());
        when(commentRepository.findAll(any(Pageable.class))).thenReturn(empty);

        Map<String, Object> result = adminService.getCommentsPaged(0, 25);

        assertThat(result.get("totalElements")).isEqualTo(0L);
        @SuppressWarnings("unchecked")
        List<?> content = (List<?>) result.get("content");
        assertThat(content).isEmpty();
    }

    // ── adminDeleteComment ───────────────────────────────────────────

    @Test
    void adminDeleteComment_callsRepositoryDeleteById() {
        adminService.adminDeleteComment(5L);

        verify(commentRepository).deleteById(5L);
    }

    // ── setApproved — farmer email ──────────────────────────────────────

    @Test
    void setApproved_farmer_approveTrue_sendsApprovalEmail() {
        when(participantRepo.findById("farmer-2")).thenReturn(Optional.of(pendingFarmer));
        when(participantRepo.save(pendingFarmer)).thenReturn(pendingFarmer);

        adminService.setApproved("farmer-2", true);

        verify(emailService).sendFarmerApproved(pendingFarmer);
    }

    @Test
    void setApproved_farmer_approveFalse_doesNotSendEmail() {
        when(participantRepo.findById("farmer-1")).thenReturn(Optional.of(farmer));
        when(participantRepo.save(farmer)).thenReturn(farmer);

        adminService.setApproved("farmer-1", false);

        verify(emailService, never()).sendFarmerApproved(any());
    }

    @Test
    void setApproved_buyer_approveTrue_doesNotSendFarmerEmail() {
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(participantRepo.save(buyer)).thenReturn(buyer);

        adminService.setApproved("buyer-1", true);

        verify(emailService, never()).sendFarmerApproved(any());
    }

    // ── getUserDetail ─────────────────────────────────────────────────────────

    @Test
    void getUserDetail_returnsMapWithUserFieldsAndOrders() {
        Order order = new Order();
        order.setId("ord-1");
        order.setStatus("PAID");
        order.setTotalAmount(150.0);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));
        when(orderRepository.findByParticipantIdOrderByOrderDateDesc("buyer-1")).thenReturn(List.of(order));

        Map<String, Object> result = adminService.getUserDetail("buyer-1");

        assertThat(result.get("id")).isEqualTo("buyer-1");
        assertThat(result.get("email")).isEqualTo("bob@example.com");
        assertThat(result.get("role")).isEqualTo("BUYER");
        @SuppressWarnings("unchecked")
        List<?> orders = (List<?>) result.get("orders");
        assertThat(orders).hasSize(1);
    }

    @Test
    void getUserDetail_throwsWhenNotFound() {
        when(participantRepo.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getUserDetail("nobody"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ── getAllOrders ──────────────────────────────────────────────────────────

    @Test
    void getAllOrders_returnsAllOrdersFromRepo() {
        Order order = new Order();
        order.setId("ord-1");
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(order));

        List<Order> result = adminService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("ord-1");
    }

    // ── issueRefund ───────────────────────────────────────────────────────────

    @Test
    void issueRefund_delegatesToRefundService() throws StripeException {
        Order order = new Order();
        order.setId("ord-1");
        when(refundService.issueRefund("ord-1", "Test reason", true)).thenReturn(order);

        Order result = adminService.issueRefund("ord-1", "Test reason");

        assertThat(result.getId()).isEqualTo("ord-1");
        verify(refundService).issueRefund("ord-1", "Test reason", true);
    }

    // ── getFinancialSummary ───────────────────────────────────────────────────

    @Test
    void getFinancialSummary_noDateFilter_sumsAllPaidOrders() {
        Order o1 = new Order(); o1.setStatus("PAID"); o1.setTotalAmount(100.0);
        o1.setOrderDate("2026-01-15T10:00:00");
        Order o2 = new Order(); o2.setStatus("ACCEPTED"); o2.setTotalAmount(200.0);
        o2.setOrderDate("2026-01-20T10:00:00");
        Order o3 = new Order(); o3.setStatus("PENDING_PAYMENT"); o3.setTotalAmount(50.0);
        o3.setOrderDate("2026-01-10T10:00:00");
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o1, o2, o3));

        Map<String, Object> result = adminService.getFinancialSummary(null, null);

        assertThat((double) result.get("totalRevenue")).isEqualTo(300.0);
        assertThat((double) result.get("platformFees")).isEqualTo(45.0);
        assertThat((double) result.get("farmerPayouts")).isEqualTo(255.0);
        assertThat(result.get("orderCount")).isEqualTo(2);
    }

    @Test
    void getFinancialSummary_withDateRange_filtersToDateRange() {
        Order o1 = new Order(); o1.setStatus("PAID"); o1.setTotalAmount(100.0);
        o1.setOrderDate("2026-01-15T10:00:00");
        Order o2 = new Order(); o2.setStatus("PAID"); o2.setTotalAmount(200.0);
        o2.setOrderDate("2026-03-01T10:00:00");
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o1, o2));

        Map<String, Object> result = adminService.getFinancialSummary("2026-01-01", "2026-02-01");

        assertThat((double) result.get("totalRevenue")).isEqualTo(100.0);
        assertThat(result.get("orderCount")).isEqualTo(1);
    }

    // ── getFinancialOrders ────────────────────────────────────────────────────

    @Test
    void getFinancialOrders_allStatus_returnsPaidOrders() {
        Order o = new Order(); o.setId("ord-1"); o.setStatus("PAID"); o.setTotalAmount(100.0);
        o.setOrderDate("2026-01-15T10:00:00"); o.setParticipantId("buyer-1");
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o));
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        List<Map<String, Object>> result = adminService.getFinancialOrders("ALL", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo("ord-1");
        assertThat(result.get(0).get("status")).isEqualTo("PAID");
        assertThat(result.get(0).get("buyerName")).isEqualTo("Bob Buyer");
    }

    @Test
    void getFinancialOrders_specificStatus_filtersOtherStatuses() {
        Order o1 = new Order(); o1.setId("ord-1"); o1.setStatus("PAID"); o1.setTotalAmount(100.0);
        o1.setOrderDate("2026-01-15T10:00:00"); o1.setParticipantId(null);
        Order o2 = new Order(); o2.setId("ord-2"); o2.setStatus("COMPLETED"); o2.setTotalAmount(200.0);
        o2.setOrderDate("2026-01-20T10:00:00"); o2.setParticipantId(null);
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o1, o2));

        List<Map<String, Object>> result = adminService.getFinancialOrders("PAID", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo("ord-1");
    }

    @Test
    void getFinancialOrders_withDateRange_filtersCorrectly() {
        Order o1 = new Order(); o1.setId("ord-1"); o1.setStatus("PAID"); o1.setTotalAmount(100.0);
        o1.setOrderDate("2026-01-15T10:00:00"); o1.setParticipantId(null);
        Order o2 = new Order(); o2.setId("ord-2"); o2.setStatus("PAID"); o2.setTotalAmount(200.0);
        o2.setOrderDate("2026-03-01T10:00:00"); o2.setParticipantId(null);
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o1, o2));

        List<Map<String, Object>> result = adminService.getFinancialOrders("ALL", "2026-01-01", "2026-02-01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo("ord-1");
    }

    @Test
    void getFinancialOrders_nonPaidStatuses_excluded() {
        Order o = new Order(); o.setId("ord-1"); o.setStatus("PENDING_PAYMENT"); o.setTotalAmount(100.0);
        o.setOrderDate("2026-01-15T10:00:00"); o.setParticipantId(null);
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(o));

        List<Map<String, Object>> result = adminService.getFinancialOrders("ALL", null, null);

        assertThat(result).isEmpty();
    }
}
