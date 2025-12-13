package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.ArtistStock;
import com.musicinvestment.musicapp.service.ArtistStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/artist-stock")
public class ArtistStockController {
    private static final Logger logger = LoggerFactory.getLogger(ArtistStockController.class);

    private final ArtistStockService artistStockService;

    public ArtistStockController(ArtistStockService artistStockService) {
        this.artistStockService = artistStockService;
    }

    @GetMapping
    public List<ArtistStock> getAllStocks() {
        return artistStockService.getAllStocks();
    }

    @GetMapping("/{artistId}")
    public Optional<ArtistStock> getStockByArtist(@PathVariable String artistId) {
        return artistStockService.getStockByArtistId(artistId);
    }

    @PostMapping("/update")
    public ArtistStock createOrUpdateStock(
            @RequestParam String artistId,
            @RequestParam BigDecimal currentPrice,
            @RequestParam BigDecimal platformBuyPrice,
            @RequestParam BigDecimal platformSellPrice,
            @RequestParam int trendingScore) {

        try {
            return artistStockService.createOrUpdateStock(artistId, currentPrice, platformBuyPrice, platformSellPrice, trendingScore);
        } catch (Exception e) {
            logger.error("Error in API call: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update artist stock. Cause: " + e.getMessage());
        }
    }
}