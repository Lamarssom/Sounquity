package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.ChatMessage;
import com.musicinvestment.musicapp.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class ChatController {

    private final ChatService chatService;

    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(@RequestParam String artistId) {
        try {
            List<ChatMessage> messages = chatService.getMessagesByArtistId(artistId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping
    public ResponseEntity<String> postMessage(@RequestBody ChatMessage message) {
        try {
            chatService.saveMessage(message);
            return ResponseEntity.ok("Message saved");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error saving message: " + e.getMessage());
        }
    }
}