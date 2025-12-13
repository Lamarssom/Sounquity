package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.ChatMessage;
import com.musicinvestment.musicapp.repository.ChatMessageRepository;
import com.musicinvestment.musicapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Autowired
    public ChatService(ChatMessageRepository chatMessageRepository, UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    public List<ChatMessage> getMessagesByArtistId(String artistId) {
        return chatMessageRepository.findByArtistId(artistId);
    }

    public void saveMessage(ChatMessage message) {
        chatMessageRepository.save(message);
    }

    public boolean userExists(String userId) {
        return userRepository.existsById(userId);
    }
}