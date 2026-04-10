package com.masterchefcuts.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.enums.Role;
import com.masterchefcuts.model.Cut;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.CutRepository;
import com.masterchefcuts.repositories.ListingRepository;
import com.masterchefcuts.repositories.OrderRepository;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.masterchefcuts.repositories.ClaimRepository;
import com.masterchefcuts.repositories.WebhookEventRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private ListingRepository listingRepository;
    @Mock private CutRepository cutRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ParticipantRepo participantRepo;
    @Mock private ClaimRepository claimRepository;
    @Mock private NotificationService notificationService;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private StripeConnectService stripeConnectService;
    @Mock private RefundService refundService;
    @Mock private EmailService emailService;

    @InjectMocks private PaymentService paymentService;

    private Participant farmer;
    private Participant buyer;
    private Listing listing;
    private Cut cut1;
    private Cut cut2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "sk_test_dummy");
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", "whsec_test");

        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        } catch (Exception ignored) {
            // not expected for this test setup
        }

        farmer = Participant.builder()
                .id("farmer-1").firstName("Jane").lastName("Farm")
                .role(Role.FARMER).email("jane@farm.com").password("pass")
                .street("1 Farm Rd").city("Rural").state("TX").zipCode("12345")
                .status("ACTIVE").approved(true).build();

        buyer = Participant.builder()
            .id("buyer-1").firstName("Pat").lastName("Buyer")
            .role(Role.BUYER).email("pat@buyer.com").password("pass")
            .street("9 Buyer St").city("Town").state("TX").zipCode("12345")
            .status("ACTIVE").approved(true).build();

        cut1 = Cut.builder().id(1L).label("Ribeye").claimed(true).claimedBy(buyer).build();
        cut2 = Cut.builder().id(2L).label("Brisket").claimed(false).build();

        listing = Listing.builder()
                .id(1L).farmer(farmer).animalType(AnimalType.BEEF).breed("Angus")
                .weightLbs(400).pricePerLb(10.0).zipCode("12345")
                .status(ListingStatus.ACTIVE).postedAt(LocalDateTime.now()).build();
        listing.getCuts().add(cut1);
        listing.getCuts().add(cut2);
        cut1.setListing(listing);
        cut2.setListing(listing);
    }

    // ── createCartIntent ──────────────────────────────────────────────────────

    @Test
    void createCartIntent_success_returnsClientSecret() throws StripeException {
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");
        when(mockIntent.getClientSecret()).thenReturn("pi_cart_secret");
        when(cutRepository.findByIdInWithListing(List.of(1L))).thenReturn(List.of(cut1));
        when(cutRepository.countByListingId(1L)).thenReturn(2L);

        try (MockedStatic<PaymentIntent> mockedStatic = mockStatic(PaymentIntent.class)) {
            mockedStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(com.stripe.net.RequestOptions.class)))
                    .thenReturn(mockIntent);

            PaymentIntentResponse response = paymentService.createCartIntent("buyer-1", List.of(1L));

            assertThat(response.getClientSecret()).isEqualTo("pi_cart_secret");
            assertThat(response.getAmountCents()).isEqualTo(200000L);
            assertThat(response.getCurrency()).isEqualTo("usd");
            verify(orderRepository).save(any(com.masterchefcuts.model.Order.class));
        }
    }

    // ── createIntent ──────────────────────────────────────────────────────────

    @Test
    void createIntent_success_calculatesAmountPerCut() throws StripeException {
        // listing: 400 lbs * $10/lb = $4000 total / 2 cuts = $2000/cut = 200000 cents
        PaymentIntentRequest req = new PaymentIntentRequest();
        req.setListingId(1L);
        req.setCutLabel("Ribeye");

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getClientSecret()).thenReturn("pi_secret_123");
        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        try (MockedStatic<PaymentIntent> mockedStatic = mockStatic(PaymentIntent.class)) {
            mockedStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenReturn(mockIntent);

            PaymentIntentResponse response = paymentService.createIntent(req);

            assertThat(response.getClientSecret()).isEqualTo("pi_secret_123");
            assertThat(response.getAmountCents()).isEqualTo(200000L); // $2000 * 100 cents
            assertThat(response.getCurrency()).isEqualTo("usd");
        }
    }

    @Test
    void createIntent_throwsWhenListingNotFound() {
        PaymentIntentRequest req = new PaymentIntentRequest();
        req.setListingId(99L);

        when(listingRepository.findByIdWithCuts(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createIntent(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Listing not found");
    }

    @Test
    void createIntent_throwsWhenListingHasNoCuts() {
        listing.getCuts().clear();
        PaymentIntentRequest req = new PaymentIntentRequest();
        req.setListingId(1L);

        when(listingRepository.findByIdWithCuts(1L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> paymentService.createIntent(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no cuts");
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Test
    void init_setsStripeApiKey() {
        paymentService.init();
        assertThat(com.stripe.Stripe.apiKey).isEqualTo("sk_test_dummy");
    }
}
