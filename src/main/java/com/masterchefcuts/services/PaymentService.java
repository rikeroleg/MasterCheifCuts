package com.masterchefcuts.services;

import com.masterchefcuts.dto.PaymentIntentRequest;
import com.masterchefcuts.dto.PaymentIntentResponse;
import com.masterchefcuts.model.Listing;
import com.masterchefcuts.repositories.ListingRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    private final ListingRepository listingRepository;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public PaymentIntentResponse createIntent(PaymentIntentRequest request) throws StripeException {
        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        int totalCuts = listing.getCuts().size();
        if (totalCuts == 0) throw new IllegalArgumentException("Listing has no cuts");

        long amountCents = Math.round((listing.getPricePerLb() * listing.getWeightLbs() / totalCuts) * 100);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency("usd")
                .putMetadata("listingId", String.valueOf(listing.getId()))
                .putMetadata("cutLabel", request.getCutLabel())
                .build();

        PaymentIntent intent = PaymentIntent.create(params);

        return new PaymentIntentResponse(intent.getClientSecret(), amountCents, "usd");
    }
}
