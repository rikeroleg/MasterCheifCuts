package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Cut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CutRepository extends JpaRepository<Cut, Long> {

    List<Cut> findByListingId(Long listingId);

    List<Cut> findByListingIdAndClaimedFalse(Long listingId);

    long countByListingIdAndClaimedTrue(Long listingId);

    long countByListingId(Long listingId);
}
