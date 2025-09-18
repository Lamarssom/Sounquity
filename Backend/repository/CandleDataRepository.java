package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandleDataRepository extends JpaRepository<CandleData, Long> {
    List<CandleData> findByArtistId(String artistId);
    List<CandleData> findByArtistIdAndTimeframe(String artistId, Timeframe timeframe);
}