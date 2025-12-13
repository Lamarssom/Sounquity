package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.ArtistStock;
import com.musicinvestment.musicapp.repository.ArtistStockRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TrendingService {

    private final ArtistStockRepository artistStockRepository;
    private final SpotifyService spotifyService;

    public TrendingService(ArtistStockRepository artistStockRepository, SpotifyService spotifyService) {
        this.artistStockRepository = artistStockRepository;
        this.spotifyService = spotifyService;
    }

    // Runs every 24 hours to update artist trending scores
    @Scheduled(cron = "0 0 0 * * *") // Executes at midnight
    public void updateTrendingScores() {
        List<ArtistStock> allArtists = artistStockRepository.findAll();

        for (ArtistStock artistStock : allArtists) {
            try {
                int newScore = spotifyService.calculateTrendingScore(artistStock.getArtistId());
                artistStock.setTrendingScore(newScore);
                artistStockRepository.save(artistStock);
            } catch (Exception e) {
                System.err.println("Error updating trending score for artist with ID " + artistStock.getArtistId() + ": " + e.getMessage());
            }
        }
    }
}