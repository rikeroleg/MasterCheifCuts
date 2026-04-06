package com.masterchefcuts.repositories;

import com.masterchefcuts.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByStripeEventId(String stripeEventId);
}
