package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findAllByOrderByCreatedAtDesc();
    boolean existsByClaimIdAndStatus(Long claimId, String status);
}
