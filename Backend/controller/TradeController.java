// src/main/java/com/musicinvestment/musicapp/controller/TradeController.java
package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.Trade;
import com.musicinvestment.musicapp.repository.TradeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/trades")
public class TradeController {
    private final TradeRepository tradeRepository;

    public TradeController(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @GetMapping("/artist/{artistId}")
    public List<Trade> getTradesByArtistId(@PathVariable String artistId) {
        return tradeRepository.findAll().stream()
                .filter(trade -> trade.getArtistId().equals(artistId))
                .toList();
    }
}