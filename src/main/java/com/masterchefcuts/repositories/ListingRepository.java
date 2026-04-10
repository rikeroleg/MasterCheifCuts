package com.masterchefcuts.repositories;

import com.masterchefcuts.enums.AnimalType;
import com.masterchefcuts.enums.ListingStatus;
import com.masterchefcuts.model.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

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

    /**
     * Combined optional-filter query used for the public listings browse page.
     * All filters are optional (null = not applied).  Status is always ACTIVE.
     * Uses native SQL so that null-checks on the string/enum columns work
     * reliably across Hibernate versions.
     */
    @Query(nativeQuery = true,
           value = "SELECT * FROM listings " +
                   "WHERE status = 'ACTIVE' " +
                   "AND (:animalType IS NULL OR animal_type = :animalType) " +
                   "AND (:maxPricePerLb IS NULL OR price_per_lb <= :maxPricePerLb) " +
                   "AND (:zipCode IS NULL OR zip_code = :zipCode) " +
                   "ORDER BY posted_at DESC",
           countQuery = "SELECT COUNT(*) FROM listings " +
                   "WHERE status = 'ACTIVE' " +
                   "AND (:animalType IS NULL OR animal_type = :animalType) " +
                   "AND (:maxPricePerLb IS NULL OR price_per_lb <= :maxPricePerLb) " +
                   "AND (:zipCode IS NULL OR zip_code = :zipCode)")
    Page<Listing> findWithFilters(
            @Param("animalType") String animalType,
            @Param("maxPricePerLb") Double maxPricePerLb,
            @Param("zipCode") String zipCode,
            Pageable pageable);
}
