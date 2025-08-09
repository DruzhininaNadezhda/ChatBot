package com.example.chatbot.controllers;

// B24EntryController.java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class B24EntryController {
    @RequestMapping(value = "/b24/handler", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
    public ResponseEntity<Void> entry() {
        return ResponseEntity.status(302).header("Location", "/b24.html").build();
    }
}
