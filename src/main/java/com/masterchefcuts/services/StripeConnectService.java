package com.masterchefcuts.services;

import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.LoginLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeConnectService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.connect.return-url}")
    private String returnUrl;

    @Value("${stripe.connect.refresh-url}")
    private String refreshUrl;

    private final ParticipantRepo participantRepo;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Creates a Stripe Express account for the farmer if they don't have one yet,
     * then returns an onboarding AccountLink URL.
     */
    @Transactional
    public String createOnboardingLink(String farmerId) throws StripeException {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new IllegalArgumentException("Farmer not found"));

        // Create account if not yet created
        if (farmer.getStripeAccountId() == null || farmer.getStripeAccountId().isBlank()) {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setEmail(farmer.getEmail())
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true).build())
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true).build())
                            .build())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                    .putMetadata("farmerId", farmerId)
                    .build();

            Account account = Account.create(params);
            farmer.setStripeAccountId(account.getId());
            participantRepo.save(farmer);
            log.info("Created Stripe Connect account {} for farmer {}", account.getId(), farmerId);
        }

        // Generate (or refresh) the onboarding link
        AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                .setAccount(farmer.getStripeAccountId())
                .setReturnUrl(returnUrl)
                .setRefreshUrl(refreshUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink link = AccountLink.create(linkParams);
        return link.getUrl();
    }

    /**
     * Returns a Stripe Express dashboard login link for a farmer who has already onboarded.
     */
    public String createDashboardLink(String farmerId) throws StripeException {
        Participant farmer = participantRepo.findById(farmerId)
                .orElseThrow(() -> new IllegalArgumentException("Farmer not found"));

        if (farmer.getStripeAccountId() == null || !Boolean.TRUE.equals(farmer.getStripeOnboardingComplete())) {
            throw new IllegalArgumentException("Stripe account not ready — please complete onboarding first");
        }

        Account account = Account.retrieve(farmer.getStripeAccountId());
        LoginLink loginLink = LoginLink.createOnAccount(farmer.getStripeAccountId());
        return loginLink.getUrl();
    }

    /**
     * Called from the webhook handler when Stripe sends account.updated.
     * Marks the farmer's onboarding as complete when charges and transfers are enabled.
     */
    @Transactional
    public void handleAccountUpdated(String stripeAccountId) {
        participantRepo.findByStripeAccountId(stripeAccountId).ifPresentOrElse(farmer -> {
            try {
                Account account = Account.retrieve(stripeAccountId);
                boolean chargesEnabled   = Boolean.TRUE.equals(account.getChargesEnabled());
                boolean transfersEnabled = Boolean.TRUE.equals(account.getPayoutsEnabled());

                if (chargesEnabled && transfersEnabled && !Boolean.TRUE.equals(farmer.getStripeOnboardingComplete())) {
                    farmer.setStripeOnboardingComplete(true);
                    participantRepo.save(farmer);
                    log.info("Farmer {} Stripe Connect onboarding complete (account {})", farmer.getId(), stripeAccountId);
                }
            } catch (StripeException e) {
                log.error("Failed to retrieve Stripe account {} for onboarding update: {}", stripeAccountId, e.getMessage());
            }
        }, () -> log.warn("Received account.updated for unknown Stripe account: {}", stripeAccountId));
    }
}
