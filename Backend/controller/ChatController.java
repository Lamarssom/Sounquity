package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.ChatMessage;
import com.musicinvestment.musicapp.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.stream.Collectors;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
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
            if (message == null) {
                System.err.println("[ChatController] Received null message payload");
                return ResponseEntity.badRequest().body("Message payload is null");
            }
            if (message.getArtistId() == null || message.getArtistId().isBlank()) {
                System.err.println("[ChatController] Invalid artistId: " + message.getArtistId());
                return ResponseEntity.badRequest().body("Invalid or missing artistId");
            }
            if (message.getUserId() == null || message.getUserId().isBlank()) {
                System.err.println("[ChatController] Invalid userId: " + message.getUserId());
                return ResponseEntity.badRequest().body("Invalid or missing userId");
            }
            // Validate userId exists in users table
            if (!chatService.userExists(message.getUserId())) {
                System.err.println("[ChatController] userId not found in users table: " + message.getUserId());
                return ResponseEntity.badRequest().body("userId does not exist in users table");
            }
            if (message.getMessage() == null || message.getMessage().isBlank()) {
                System.err.println("[ChatController] Invalid message: " + message.getMessage());
                return ResponseEntity.badRequest().body("Invalid or missing message");
            }
            chatService.saveMessage(message);
            messagingTemplate.convertAndSend("/topic/messages/" + message.getArtistId(), message);
            System.out.println("[ChatController] Message saved and broadcasted for artistId: " + message.getArtistId());
            return ResponseEntity.ok("Message saved");
        } catch (ConstraintViolationException e) {
            String errorMsg = "Validation error: " + e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining(", "));
            System.err.println("[ChatController] " + errorMsg);
            return ResponseEntity.badRequest().body(errorMsg);
        } catch (DataIntegrityViolationException e) {
            String errorMsg = "Database error: " + e.getMostSpecificCause().getMessage();
            System.err.println("[ChatController] " + errorMsg);
            return ResponseEntity.status(500).body(errorMsg);
        } catch (Exception e) {
            String errorMsg = "Unexpected error saving message: " + e.getMessage();
            System.err.println("[ChatController] " + errorMsg);
            e.printStackTrace();
            return ResponseEntity.status(500).body(errorMsg);
        }
    }
}
