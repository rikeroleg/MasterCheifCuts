package com.masterchefcuts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.model.Order;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
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
class RefundServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private ClaimRepository claimRepository;
    @Mock private NotificationService notificationService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private RefundService refundService;

    private Order paidOrder;
    private Participant buyer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refundService, "stripeSecretKey", "sk_test_dummy");

        buyer = Participant.builder()
                .id("buyer-1").firstName("Pat").lastName("Buyer")
                .role(Role.BUYER).email("pat@buyer.com").password("pass")
                .street("1 Main St").city("Town").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).totalSpent(100.0).build();

        paidOrder = new Order();
        paidOrder.setId("order-1");
        paidOrder.setStatus("PAID");
        paidOrder.setParticipantId("buyer-1");
        paidOrder.setStripePaymentIntentId("pi_test_123");
        paidOrder.setAmountCents(5000L);
    }

    // ── issueRefund — guard clauses ───────────────────────────────────────────

    @Test
    void issueRefund_orderNotFound_throwsIllegalArgument() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.issueRefund("missing", "test", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order not found");
    }

    @Test
    void issueRefund_alreadyRefunded_throwsIllegalArgument() {
        paidOrder.setStatus("REFUNDED");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> refundService.issueRefund("order-1", "duplicate", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order has already been refunded");
    }

    @Test
    void issueRefund_pendingPayment_throwsIllegalArgument() {
        paidOrder.setStatus("PENDING_PAYMENT");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> refundService.issueRefund("order-1", "never paid", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot refund an order that was never paid");
    }

    @Test
    void issueRefund_paymentFailed_throwsIllegalArgument() {
        paidOrder.setStatus("PAYMENT_FAILED");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> refundService.issueRefund("order-1", "failed", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot refund an order that was never paid");
    }

    @Test
    void issueRefund_noPaymentIntent_throwsIllegalArgument() {
        paidOrder.setStripePaymentIntentId(null);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> refundService.issueRefund("order-1", "no intent", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order has no associated Stripe payment");
    }

    // ── issueRefund — happy path ──────────────────────────────────────────────

    @Test
    void issueRefund_fullRefund_updatesOrderStatusAndReversesBuyerSpend() throws StripeException {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));
        when(orderRepository.save(any())).thenReturn(paidOrder);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getLatestCharge()).thenReturn("ch_test_123");

        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_test_123");
        when(mockRefund.getAmount()).thenReturn(5000L);

        try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> mockedRefund = mockStatic(Refund.class)) {

            mockedIntent.when(() -> PaymentIntent.retrieve("pi_test_123")).thenReturn(mockIntent);
            mockedRefund.when(() -> Refund.create(any(RefundCreateParams.class))).thenReturn(mockRefund);

            Order result = refundService.issueRefund("order-1", "Customer request", true);

            assertThat(result.getStatus()).isEqualTo("REFUNDED");
            assertThat(result.getNotes()).contains("re_test_123").contains("Customer request");
            assertThat(buyer.getTotalSpent()).isEqualTo(50.0); // 100 - (5000/100)
            verify(participantRepo).save(buyer);
        }
    }

    @Test
    void issueRefund_noChargeOnIntent_throwsIllegalArgument() throws StripeException {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(paidOrder));

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getLatestCharge()).thenReturn(null);

        try (MockedStatic<PaymentIntent> mockedIntent = mockStatic(PaymentIntent.class)) {
            mockedIntent.when(() -> PaymentIntent.retrieve("pi_test_123")).thenReturn(mockIntent);

            assertThatThrownBy(() -> refundService.issueRefund("order-1", "no charge", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No charge found");
        }
    }

    // ── handleChargeRefunded ──────────────────────────────────────────────────

    @Test
    void handleChargeRefunded_knownIntent_marksOrderRefunded() {
        when(orderRepository.findByStripePaymentIntentId("pi_test_123"))
                .thenReturn(Optional.of(paidOrder));
        when(orderRepository.save(any())).thenReturn(paidOrder);
        when(participantRepo.findById("buyer-1")).thenReturn(Optional.of(buyer));

        refundService.handleChargeRefunded("pi_test_123", 5000L);

        assertThat(paidOrder.getStatus()).isEqualTo("REFUNDED");
        verify(orderRepository).save(paidOrder);
    }

    @Test
    void handleChargeRefunded_alreadyRefunded_doesNotSaveAgain() {
        paidOrder.setStatus("REFUNDED");
        when(orderRepository.findByStripePaymentIntentId("pi_test_123"))
                .thenReturn(Optional.of(paidOrder));

        refundService.handleChargeRefunded("pi_test_123", 5000L);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleChargeRefunded_unknownIntent_doesNothing() {
        when(orderRepository.findByStripePaymentIntentId("pi_unknown"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> refundService.handleChargeRefunded("pi_unknown", 1000L))
                .doesNotThrowAnyException();
        verify(orderRepository, never()).save(any());
    }

    // ── handleDisputeCreated ──────────────────────────────────────────────────

    @Test
    void handleDisputeCreated_knownIntent_marksOrderDisputed() {
        when(orderRepository.findByStripePaymentIntentId("pi_test_123"))
                .thenReturn(Optional.of(paidOrder));
        when(orderRepository.save(any())).thenReturn(paidOrder);

        refundService.handleDisputeCreated("pi_test_123", "dp_123", 5000L);

        assertThat(paidOrder.getStatus()).isEqualTo("DISPUTED");
        assertThat(paidOrder.getNotes()).contains("dp_123");
        verify(orderRepository).save(paidOrder);
    }

    @Test
    void handleDisputeCreated_alreadyDisputed_doesNotSaveAgain() {
        paidOrder.setStatus("DISPUTED");
        when(orderRepository.findByStripePaymentIntentId("pi_test_123"))
                .thenReturn(Optional.of(paidOrder));

        refundService.handleDisputeCreated("pi_test_123", "dp_123", 5000L);

        verify(orderRepository, never()).save(any());
    }
}
