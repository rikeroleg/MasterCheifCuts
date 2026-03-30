package com.masterchefcuts.repositories;

import com.masterchefcuts.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    boolean existsByBuyerIdAndListingId(String buyerId, Long listingId);
    Optional<WaitlistEntry> findByBuyerIdAndListingId(String buyerId, Long listingId);
    List<WaitlistEntry> findByListingIdOrderByJoinedAtAsc(Long listingId);
    void deleteByBuyerIdAndListingId(String buyerId, Long listingId);
}
