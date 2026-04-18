package com.masterchefcuts.repositories;

import com.masterchefcuts.enums.AnimalRequestStatus;
import com.masterchefcuts.model.AnimalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnimalRequestRepository extends JpaRepository<AnimalRequest, Long> {

    List<AnimalRequest> findByStatusOrderByCreatedAtDesc(AnimalRequestStatus status);

    List<AnimalRequest> findByStatusInOrderByCreatedAtDesc(List<AnimalRequestStatus> statuses);

    List<AnimalRequest> findByBuyerIdOrderByCreatedAtDesc(String buyerId);
}