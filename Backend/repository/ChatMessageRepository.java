package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {
    List<ChatMessage> findByArtistId(String artistId);
}