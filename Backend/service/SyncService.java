package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SyncService {

    private final ArtistService artistService;
    private final BlockchainSyncService blockchainSyncService;
    private final CandleDataService candleDataService;
    private final SpotifyService spotifyService;
    private final ContractService contractService;

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
                    System.out.println("Skipping sync for artistId " + artistId + ": No valid contract address");
                    continue;
                }

                // Update price and volume
                BigDecimal currentPrice = blockchainSyncService.getCurrentPriceRaw(artistId);
                BigDecimal totalVolume = blockchainSyncService.getTotalVolumeTradedRaw(artistId);
                if (currentPrice.equals(BigDecimal.ZERO) || totalVolume.equals(BigDecimal.ZERO)) {
                    System.out.println("Skipping sync for artistId " + artistId + ": Invalid price or volume");
                    continue;
                }

                artist.setCurrentPrice(currentPrice.doubleValue());
                artist.setTotalVolume(totalVolume.intValue());
                artistService.updateArtistInfo(artistId, ArtistSharesDto.fromArtist(artist));

                // Fetch and save candle data for all timeframes
                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                long[] timeframes = {60, 300, 900, 3600, 14400, 86400}; // 1m, 5m, 15m, 1h, 4h, 1D
                Timeframe[] timeframeEnums = {
                    Timeframe.ONE_MINUTE,
                    Timeframe.FIVE_MINUTES,
                    Timeframe.FIFTEEN_MINUTES,
                    Timeframe.ONE_HOUR,
                    Timeframe.FOUR_HOURS,
                    Timeframe.ONE_DAY
                };

                for (int i = 0; i < timeframes.length; i++) {
                    long timeframe = timeframes[i];
                    Timeframe timeframeEnum = timeframeEnums[i];
                    Tuple6<List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>, List<BigInteger>> candleHistory =
                        token.getCandleHistory(BigInteger.valueOf(timeframe)).send();
                    if (!candleHistory.component1().isEmpty()) {
                        // Use the latest candle
                        CandleData candleData = new CandleData();
                        candleData.setArtistId(artistId);
                        candleData.setTimeframe(timeframeEnum);
                        candleData.setTimestamp(LocalDateTime.now());
                        candleData.setOpen(new BigDecimal(candleHistory.component1().get(0)).divide(new BigDecimal("100000000")));
                        candleData.setHigh(new BigDecimal(candleHistory.component2().get(0)).divide(new BigDecimal("100000000")));
                        candleData.setLow(new BigDecimal(candleHistory.component3().get(0)).divide(new BigDecimal("100000000")));
                        candleData.setClose(new BigDecimal(candleHistory.component4().get(0)).divide(new BigDecimal("100000000")));
                        candleData.setVolume(new BigDecimal(candleHistory.component5().get(0)));
                        candleDataService.saveCandleData(candleData);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Blockchain sync error: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Spotify sync error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
