package com.masterchefcuts.repositories;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.model.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    List<Listing> findByStatusOrderByPostedAtDesc(ListingStatus status);
    Page<Listing> findByStatusOrderByPostedAtDesc(ListingStatus status, Pageable pageable);

    List<Listing> findByZipCodeAndStatusOrderByPostedAtDesc(String zipCode, ListingStatus status);
    Page<Listing> findByZipCodeAndStatusOrderByPostedAtDesc(String zipCode, ListingStatus status, Pageable pageable);

    List<Listing> findByAnimalTypeAndStatusOrderByPostedAtDesc(AnimalType animalType, ListingStatus status);
    Page<Listing> findByAnimalTypeAndStatusOrderByPostedAtDesc(AnimalType animalType, ListingStatus status, Pageable pageable);

    List<Listing> findByFarmerIdOrderByPostedAtDesc(String farmerId);
    Page<Listing> findByFarmerIdOrderByPostedAtDesc(String farmerId, Pageable pageable);

    @Query("SELECT l FROM Listing l LEFT JOIN FETCH l.cuts WHERE l.id = :id")
    Optional<Listing> findByIdWithCuts(@Param("id") Long id);

}

