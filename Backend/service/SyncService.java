package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SyncService {

    private final ArtistService artistService;
    private final BlockchainSyncService blockchainSyncService;
    private final CandleDataService candleDataService;
    private final SpotifyService spotifyService;
    private final ContractService contractService;

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    @Autowired
    public SyncService(ArtistService artistService, BlockchainSyncService blockchainSyncService,
                       CandleDataService candleDataService, SpotifyService spotifyService,
                       ContractService contractService) {
        this.artistService = artistService;
        this.blockchainSyncService = blockchainSyncService;
        this.candleDataService = candleDataService;
        this.spotifyService = spotifyService;
        this.contractService = contractService;
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void syncBlockchainData() {
        try {
            List<Artist> artists = artistService.getAllArtists();
            for (Artist artist : artists) {
                String artistId = artist.getId();
                String contractAddress = artist.getContractAddress();
                if (contractAddress == null || contractAddress.equals("0x0000000000000000000000000000000000000000")) {
                    logger.info("Skipping sync for artistId {}: No valid contract address", artistId);
                    continue;
                }

                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                BigDecimal currentPrice = contractService.getCurrentPriceRaw(artistId);
                BigDecimal totalVolume = contractService.getTotalVolumeTradedRaw(artistId);
                BigInteger dailySellLimitUsd;
                boolean curveComplete;

                try {
                    dailySellLimitUsd = token.dailySellLimitUsd().send();
                    curveComplete = token.curveComplete().send();
                } catch (Exception e) {
                    logger.warn("Failed to fetch dailySellLimitUsd or curveComplete for artistId {}, assuming defaults: {}", artistId, e.getMessage());
                    dailySellLimitUsd = BigInteger.valueOf(50_000).multiply(BigInteger.TEN.pow(8));
                    curveComplete = false;
                }

                if (currentPrice.equals(BigDecimal.ZERO) || totalVolume.equals(BigDecimal.ZERO)) {
                    logger.warn("Skipping sync for artistId {}: Invalid price or volume", artistId);
                    continue;
                }

                artist.setCurrentPrice(currentPrice.doubleValue());
                artist.setTotalVolume(totalVolume.intValue());
                artist.setDailyLiquidity(new BigDecimal(dailySellLimitUsd).divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP));
                artist.setCurveComplete(curveComplete);

                artistService.updateArtistInfo(artistId, ArtistSharesDto.fromArtist(artist));

                logger.info("Sync completed for artist {}: price=${}, volume={}, dailySellLimitUsd=${}, curveComplete={}",
                    artist.getName(), currentPrice, totalVolume, dailySellLimitUsd, curveComplete);
            }
        } catch (Exception e) {
            logger.error("Blockchain sync error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    public void syncSpotifyData() {
        try {
            List<Artist> artists = artistService.getAllArtists();
            for (Artist artist : artists) {
                Artist spotifyArtist = spotifyService.getArtistById(artist.getId());
                if (spotifyArtist != null) {
                    ArtistSharesDto spotifyData = ArtistSharesDto.fromArtist(spotifyArtist);
                    artistService.updateArtistInfo(artist.getId(), spotifyData);
                }
            }
        } catch (Exception e) {
            logger.error("Spotify sync error: {}", e.getMessage(), e);
        }
    }
}
