package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Find order by payment intent ID with pessimistic write lock to prevent race conditions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.stripePaymentIntentId = :intentId")
    Optional<Order> findByStripePaymentIntentIdForUpdate(String intentId);

    List<Order> findByParticipantIdOrderByOrderDateDesc(String participantId);

    List<Order> findAllByOrderByOrderDateDesc();

    /**
     * Check if an order already exists for the given participant and cut IDs (to prevent duplicates)
     */
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.participantId = :participantId " +
           "AND o.status IN ('PENDING_PAYMENT', 'PAID') " +
           "AND o.items LIKE %:cutIdPattern%")
    boolean existsPendingOrderWithCut(String participantId, String cutIdPattern);
}
