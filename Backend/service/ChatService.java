package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.ChatMessage;
import com.musicinvestment.musicapp.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    @Autowired
    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatMessage> getMessagesByArtistId(String artistId) {
        return chatMessageRepository.findByArtistId(artistId);
    }

    public void saveMessage(ChatMessage message) {
        chatMessageRepository.save(message);
    }
}