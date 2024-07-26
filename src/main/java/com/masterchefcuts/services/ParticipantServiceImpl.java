package com.masterchefcuts.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;

@Service
public class ParticipantServiceImpl implements ParticipantService {

    @Autowired
    public ParticipantRepo participantRepo;

    @Override
    public Participant addParticipant(Participant participant) {
        return participantRepo.save(participant);
    }
    
}
