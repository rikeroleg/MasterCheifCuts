package com.masterchefcuts.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masterchefcuts.model.Participant;
import com.masterchefcuts.repositories.ParticipantRepo;

@Service
@Transactional(rollbackFor = Exception.class)
public class ParticipantServiceImpl implements ParticipantService {

    @Autowired
    public ParticipantRepo participantRepo;

    @Override
    public Participant addParticipant(Participant participant) {
        return participantRepo.save(participant);
    }

    @Override
    public Participant findById(String id) {
        return participantRepo.findById(id).orElseThrow(() -> new RuntimeException("Participant not Found")); 
    }

    // @Override
    // public void deleteParticipant(String id) {
    //     return participantRepo.delete(id);
    // }
}
