package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private static final BigDecimal USD_CENT_SCALE = new BigDecimal("100000000"); // 10^8

    private final ArtistService artistService;
    private final BlockchainSyncService blockchainSyncService;
    private final CandleDataService candleDataService;
    private final SpotifyService spotifyService;
    private final ContractService contractService;
    private final TradeRepository tradeRepository;

    @Autowired
    public SyncService(
            ArtistService artistService,
            BlockchainSyncService blockchainSyncService,
            CandleDataService candleDataService,
            SpotifyService spotifyService,
            ContractService contractService,
            TradeRepository tradeRepository) {
        this.artistService = artistService;
        this.blockchainSyncService = blockchainSyncService;
        this.candleDataService = candleDataService;
        this.spotifyService = spotifyService;
        this.contractService = contractService;
        this.tradeRepository = tradeRepository;
    }

    @Scheduled(fixedRateString = "${sync.blockchain.interval:300000}")
    @Transactional
    public void syncBlockchainData() {
        logger.info("Starting blockchain sync job...");
        try {
            List<Artist> artists = artistService.getAllArtists();
            int syncedCount = 0;

            for (Artist artist : artists) {
                if (syncArtistBlockchain(artist)) {
                    syncedCount++;
                }
            }

            logger.info("Blockchain sync completed: {}/{} artists updated", syncedCount, artists.size());
        } catch (Exception e) {
            logger.error("Critical error in blockchain sync", e);
        }
    }

    private boolean syncArtistBlockchain(Artist artist) {
        String artistId = artist.getId();
        String contractAddress = artist.getContractAddress();

        if (contractAddress == null || contractAddress.equals("0x0000000000000000000000000000000000000000")) {
            logger.debug("Skipping artist {}: no contract deployed", artistId);
            return false;
        }

        try {
            ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
            BigDecimal currentPrice = contractService.getCurrentPriceRaw(artistId);
            BigDecimal totalVolume = contractService.getTotalVolumeTradedRaw(artistId);

            BigInteger dailySellLimitUsd;
            boolean curveComplete;
            try {
                dailySellLimitUsd = token.dailySellLimitUsd().send();
                curveComplete = token.curveComplete().send();
            } catch (Exception e) {
                logger.warn("Failed to fetch dailySellLimitUsd/curveComplete for artistId {}, using defaults", artistId, e);
                dailySellLimitUsd = BigInteger.valueOf(50_000).multiply(BigInteger.TEN.pow(8));
                curveComplete = false;
            }

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0 ||
                totalVolume == null || totalVolume.compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Invalid price/volume for artistId {}: price={}, volume={}", artistId, currentPrice, totalVolume);
                return false;
            }

            // Update artist fields with proper types
            artist.setCurrentPrice(currentPrice);
            // Calculate real 24h USD volume from actual trades
            LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("UTC")).minusHours(24);
            BigDecimal volume24hUsd = tradeRepository.sumVolume24hUsd(artistId, cutoff);
            artist.setVolume(volume24hUsd.setScale(2, RoundingMode.HALF_UP)); // assuming column is 'volume'
            artist.setDailyLiquidity(new BigDecimal(dailySellLimitUsd)
                    .divide(USD_CENT_SCALE, 2, RoundingMode.HALF_UP));
            artist.setCurveComplete(curveComplete);

            artistService.updateArtistInfo(artistId, ArtistSharesDto.fromArtist(artist));
            logger.info("Synced artist {}: price=${}, volume=${}, dailyLimit=${}, curveComplete={}",
                    artist.getName(), currentPrice, totalVolume,
                    artist.getDailyLiquidity(), curveComplete);

            return true;

        } catch (Exception e) {
            logger.error("Failed to sync artistId {}: {}", artistId, e.getMessage(), e);
            return false;
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void syncSpotifyData() {
        logger.info("Starting Spotify sync job...");
        try {
            List<Artist> artists = artistService.getAllArtists();
            int syncedCount = 0;

            for (Artist artist : artists) {
                String spotifyId = artist.getSpotifyId();
                if (spotifyId == null || spotifyId.isBlank()) {
                    logger.debug("Skipping Spotify sync for artist {}: no Spotify ID", artist.getId());
                    continue;
                }

                try {
                    Artist spotifyArtist = spotifyService.getArtistById(spotifyId);
                    if (spotifyArtist != null) {
                        artistService.updateArtistInfo(artist.getId(), ArtistSharesDto.fromArtist(spotifyArtist));
                        syncedCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to sync Spotify data for artist {}: {}", artist.getId(), e.getMessage());
                }
            }

            logger.info("Spotify sync completed: {}/{} artists updated", syncedCount, artists.size());
        } catch (Exception e) {
            logger.error("Critical error in Spotify sync", e);
        }
    }
}