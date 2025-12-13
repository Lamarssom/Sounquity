package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.repository.CandleDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CandleDataService {

    private static final Logger logger = LoggerFactory.getLogger(CandleDataService.class);

    @Autowired
    private CandleDataRepository candleDataRepository;

    public void saveCandleData(CandleData candleData) {
        try {
            candleDataRepository.save(candleData);
            logger.debug("Successfully saved candle data: artistId={}, timeframe={}, timestamp={}", 
                    candleData.getArtistId(), candleData.getTimeframe(), candleData.getTimestamp());
        } catch (Exception e) {
            logger.error("Failed to save candle data: artistId={}, timeframe={}, timestamp={}, error={}", 
                    candleData.getArtistId(), candleData.getTimeframe(), candleData.getTimestamp(), e.getMessage());
            throw e; // Re-throw to ensure BlockchainSyncService logs the error
        }
    }

    public List<CandleData> getCandleDataByArtistIdAndTimeframe(String artistId, Timeframe timeframe) {
        logger.debug("Fetching candle data for artistId={}, timeframe={}", artistId, timeframe);
        List<CandleData> candles = candleDataRepository.findByArtistIdAndTimeframe(artistId, timeframe);
        logger.debug("Fetched {} candles for artistId={}, timeframe={}", candles.size(), artistId, timeframe);
        return candles;
    }
}