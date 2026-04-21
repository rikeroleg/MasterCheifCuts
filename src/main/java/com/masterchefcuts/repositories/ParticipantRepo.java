package com.masterchefcuts.repositories;

import com.masterchefcuts.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import com.masterchefcuts.model.Participant;
import org.springframework.stereotype.Repository;

@Repository
public interface ParticipantRepo extends JpaRepository<Participant, String> {

    java.util.Optional<Participant> findByEmail(String email);

    java.util.List<Participant> findByRole(Role role);

    boolean existsByEmail(String email);

    java.util.Optional<Participant> findByResetToken(String resetToken);
    java.util.Optional<Participant> findByVerificationToken(String verificationToken);
    java.util.Optional<Participant> findByStripeAccountId(String stripeAccountId);
    java.util.Optional<Participant> findByRefreshToken(String refreshToken);

    java.util.List<Participant> findByZipCodeAndEmailVerifiedTrue(String zipCode);
}
