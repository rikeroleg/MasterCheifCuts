package com.masterchefcuts.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.masterchefcuts.model.Participant;
import org.springframework.stereotype.Repository;

@Repository
public interface ParticipantRepo extends JpaRepository<Participant, String> {    
}
