package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.ChatMessage;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.repository.ChatMessageRepository;
import com.musicinvestment.musicapp.repository.CandleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private CandleDataRepository candleDataRepository;

    @GetMapping("/messages")
    public List<ChatMessage> getMessages() {
        return chatMessageRepository.findAll();
    }

    @GetMapping("/candles")
    public List<CandleData> getCandles() {
        return candleDataRepository.findAll();
    }
}