package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.ArtistStock;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.repository.ArtistStockRepository;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ArtistStockService {
    private static final Logger logger = LoggerFactory.getLogger(ArtistStockService.class);

    private final ArtistStockRepository artistStockRepository;
    private final ArtistRepository artistRepository;

    public ArtistStockService(ArtistStockRepository artistStockRepository, ArtistRepository artistRepository) {
        this.artistStockRepository = artistStockRepository;
        this.artistRepository = artistRepository;
    }

    public List<ArtistStock> getAllStocks() {
        return artistStockRepository.findAll();
    }

    public Optional<ArtistStock> getStockByArtistId(String artistId) {
        return artistStockRepository.findByArtistId(artistId);
    }

    public ArtistStock createOrUpdateStock(String artistId, BigDecimal currentPrice, BigDecimal platformBuyPrice, BigDecimal platformSellPrice, int trendingScore) {
        logger.info("Checking if artist stock exists for ID: {}", artistId);

        Optional<ArtistStock> existingStock = artistStockRepository.findByArtistId(artistId);
        ArtistStock artistStock = existingStock.orElse(new ArtistStock());
        
        // Ensure artist ID is set
        artistStock.setArtistId(artistId);

        artistStock.setCurrentPrice(currentPrice);
        artistStock.setPlatformBuyPrice(platformBuyPrice);
        artistStock.setPlatformSellPrice(platformSellPrice);
        artistStock.setTrendingScore(trendingScore);

        ArtistStock savedStock = artistStockRepository.save(artistStock);
        logger.info("Artist Stock Saved Successfully: {}", savedStock);
        return savedStock;
    }
}