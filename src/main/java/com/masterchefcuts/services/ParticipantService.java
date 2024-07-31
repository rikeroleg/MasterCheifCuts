package com.masterchefcuts.services;

import com.masterchefcuts.model.Participant;

public interface ParticipantService {
    
    public Participant addParticipant(Participant participant);
    public Participant findById(String id);
}
