package com.masterchefcuts.repositories;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findByStatusOrderByPostedAtDesc(ListingStatus status);

    List<Listing> findByZipCodeAndStatusOrderByPostedAtDesc(String zipCode, ListingStatus status);

    List<Listing> findByAnimalTypeAndStatusOrderByPostedAtDesc(AnimalType animalType, ListingStatus status);

    List<Listing> findByFarmerIdOrderByPostedAtDesc(String farmerId);
}
