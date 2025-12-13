package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.ArtistStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.sql.Timestamp;
import java.util.Optional;

public interface ArtistStockRepository extends JpaRepository<ArtistStock, Long> {
    Optional<ArtistStock> findByArtistId(String artistId);
    Optional<ArtistStock> findByArtistName(String artistName);

    // Query to find the artist's stock price at or after the start of the day (midnight)
    @Query("SELECT a FROM ArtistStock a WHERE a.artistId = :artistId AND a.timestamp >= :startOfDay ORDER BY a.timestamp ASC")
    Optional<ArtistStock> findByArtistIdAndDate(String artistId, Timestamp startOfDay);
    
    // Add an optional query to find the first record of the artist stock price at the start of the day
    @Query("SELECT a FROM ArtistStock a WHERE a.artistId = :artistId AND a.timestamp >= :startOfDay ORDER BY a.timestamp ASC")
    Optional<ArtistStock> findOpeningPriceByArtistIdAndDate(String artistId, Timestamp startOfDay);
}