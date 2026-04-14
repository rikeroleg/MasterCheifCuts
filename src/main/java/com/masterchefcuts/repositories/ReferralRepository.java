package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Long> {
    List<Referral> findByReferrerId(String referrerId);
    boolean existsByReferredId(String referredId);
}
