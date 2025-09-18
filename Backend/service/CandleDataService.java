package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.repository.CandleDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CandleDataService {

    private final CandleDataRepository candleDataRepository;

    @Autowired
    public CandleDataService(CandleDataRepository candleDataRepository) {
        this.candleDataRepository = candleDataRepository;
    }

    public List<CandleData> getCandleDataByArtistId(String artistId) {
        return candleDataRepository.findByArtistId(artistId);
    }

    public List<CandleData> getCandleDataByArtistIdAndTimeframe(String artistId, Timeframe timeframe) {
        return candleDataRepository.findByArtistIdAndTimeframe(artistId, timeframe);
    }

    public void saveCandleData(CandleData candleData) {
        candleDataRepository.save(candleData);
    }
}