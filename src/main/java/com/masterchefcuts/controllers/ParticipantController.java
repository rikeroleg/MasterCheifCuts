package com.masterchefcuts.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.ParticipantService;

@RestController
@RequestMapping("/api/participants")
@CrossOrigin(origins = "*")
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
