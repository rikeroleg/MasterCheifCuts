package com.masterchefcuts.repositories;

import com.masterchefcuts.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.author WHERE c.listing.id = :listingId ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.listing.id = :listingId")
    Page<Comment> findByListingIdWithAuthor(@Param("listingId") Long listingId, Pageable pageable);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.listing.id = :listingId ORDER BY c.createdAt DESC")
    List<Comment> findByListingIdWithAuthor(@Param("listingId") Long listingId);

    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.id = :id")
    Optional<Comment> findByIdWithAuthor(@Param("id") Long id);
}
