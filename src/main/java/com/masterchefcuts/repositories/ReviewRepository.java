package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByListingIdOrderByCreatedAtDesc(Long listingId);
    boolean existsByBuyerIdAndListingId(String buyerId, Long listingId);
    Optional<Review> findByBuyerIdAndListingId(String buyerId, Long listingId);
}
