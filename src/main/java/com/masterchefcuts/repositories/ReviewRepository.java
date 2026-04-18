package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByListingIdOrderByCreatedAtDesc(Long listingId);
    List<Review> findByListingFarmerIdOrderByCreatedAtDesc(String farmerId);
    boolean existsByBuyerIdAndListingId(String buyerId, Long listingId);
    Optional<Review> findByBuyerIdAndListingId(String buyerId, Long listingId);

    /** Top reviews with a comment, rating ≥ 4, ordered by rating desc then newest. */
    @Query("SELECT r FROM Review r WHERE r.rating >= 4 AND r.comment IS NOT NULL AND LENGTH(r.comment) > 10 ORDER BY r.rating DESC, r.createdAt DESC")
    List<Review> findFeatured(org.springframework.data.domain.Pageable pageable);
}
