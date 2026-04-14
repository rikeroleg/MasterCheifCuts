package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @Query("""
            select c
            from Claim c
            join fetch c.listing l
            join fetch c.cut cut
            where c.buyer.id = :buyerId
            order by c.claimedAt desc
            """)
    List<Claim> findClaimSummariesByBuyerId(@Param("buyerId") String buyerId);

    List<Claim> findByBuyerIdOrderByClaimedAtDesc(String buyerId);

    List<Claim> findByListingIdOrderByClaimedAtAsc(Long listingId);

    boolean existsByCutId(Long cutId);

    List<Claim> findByPaidFalseAndExpiresAtBefore(LocalDateTime now);

    List<Claim> findByCutIdIn(List<Long> cutIds);
}
