package com.masterchefcuts.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
// import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.ParticipantService;

@Controller
public class ParticipantController {
    @Autowired
    private ParticipantService participantService;

    // @CrossOrigin
    @PostMapping("/add")
    public String add(@RequestBody Participant participant) {
        participantService.addParticipant(participant);
        return "Participant added successfully";
    }
        
}
