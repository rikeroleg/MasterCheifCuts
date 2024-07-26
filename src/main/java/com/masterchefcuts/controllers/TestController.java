package com.masterchefcuts.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.masterchefcuts.model.Participant;
import com.masterchefcuts.services.ParticipantService;

@Controller
public class TestController {
    @Autowired
    private ParticipantService participantService;
        
    // @CrossOrigin
    // @RequestMapping("/hi")
    // public String index(@RequestParam(name="name", required=false, defaultValue="World") String name) {
    //     return name;
    // }

    @CrossOrigin
    @PostMapping("/add")
    public String add(@RequestBody Participant participant) {
        participantService.addParticipant(participant);
        return "Participant added successfully";
    }
        
}
