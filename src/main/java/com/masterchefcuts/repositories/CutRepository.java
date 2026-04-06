package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Cut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CutRepository extends JpaRepository<Cut, Long> {

    List<Cut> findByListingId(Long listingId);

    List<Cut> findByListingIdAndClaimedFalse(Long listingId);

    @Query("SELECT c FROM Cut c JOIN FETCH c.listing WHERE c.id IN :ids")
    List<Cut> findByIdInWithListing(@Param("ids") List<Long> ids);

    long countByListingIdAndClaimedTrue(Long listingId);

    long countByListingId(Long listingId);
}
