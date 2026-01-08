package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.service.ArtistService;
import com.musicinvestment.musicapp.service.BlockchainSyncService;
import com.musicinvestment.musicapp.service.ContractService;
import com.musicinvestment.musicapp.service.CandleDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private static final Logger logger = LoggerFactory.getLogger(ArtistController.class);

    private final ArtistService artistService;
    private final CandleDataService candleDataService;
    private final BlockchainSyncService blockchainSyncService;
    private final ContractService contractService;  // ADD THIS LINE

    @Autowired
    public ArtistController(
            ArtistService artistService,
            CandleDataService candleDataService,
            BlockchainSyncService blockchainSyncService,
            ContractService contractService) {  // ADD THIS PARAMETER
        this.artistService = artistService;
        this.candleDataService = candleDataService;
        this.blockchainSyncService = blockchainSyncService;
        this.contractService = contractService;     // ASSIGN IT
        logger.info("ArtistController initialized");
    }

    @GetMapping
    public List<ArtistSharesDto> getAllArtists() {
        logger.info("Fetching all artists");
        List<Artist> artists = artistService.getAllArtists();
        logger.info("Fetched artists count: {}", artists.size());
        return artists.stream()
                     .sorted((a, b) -> Integer.compare(b.getPopularity(), a.getPopularity()))
                     .map(ArtistSharesDto::fromArtist)
                     .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ArtistSharesDto getArtistById(@PathVariable String id) {
        logger.info("Fetching artist by ID: {}", id);
        Optional<Artist> artistOptional = artistService.getArtistById(id);
        if (artistOptional.isPresent()) {
            return ArtistSharesDto.fromArtist(artistOptional.get());
        } else {
            logger.warn("Artist not found for ID: {}", id);
            throw new RuntimeException("Artist not found: " + id);
        }
    }

    @GetMapping("/search")
    public List<ArtistSharesDto> searchArtistsByName(@RequestParam String name) {
        logger.info("Searching artists by name: {}", name);
        List<Artist> artists = artistService.searchArtistsByName(name);
        return artists.stream()
                     .map(ArtistSharesDto::fromArtist)
                     .collect(Collectors.toList());
    }

    @GetMapping("/contract/{id}")
    public String getArtistContractAddress(@PathVariable String id) {
        logger.info("Fetching contract address for artist ID: {}", id);
        Optional<String> contractAddress = artistService.getArtistContractAddress(id);
        if (contractAddress.isPresent()) {
            return contractAddress.get();
        } else {
            logger.warn("Contract address not found for artist ID: {}", id);
            throw new RuntimeException("Contract address not found for artist: " + id);
        }
    }

    @GetMapping("/by-contract/{contractAddress}")
    public ResponseEntity<ArtistSharesDto> getArtistByContractAddress(@PathVariable String contractAddress) {
        logger.info("Fetching artist by contract address: {}", contractAddress);
        Optional<Artist> artistOptional = artistService.getArtistByContractAddress(contractAddress);
        if (artistOptional.isPresent()) {
            return ResponseEntity.ok(ArtistSharesDto.fromArtist(artistOptional.get()));
        } else {
            logger.warn("Artist not found for contract address: {}", contractAddress);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/test")
    public String testEndpoint() {
        logger.info("Test endpoint accessed");
        return "Artist API is working!";
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateArtistInfo(
            @PathVariable String id,
            @RequestBody ArtistSharesDto artistSharesDto) {
        logger.info("Updating artist info for ID: {}", id);
        artistService.updateArtistInfo(id, artistSharesDto);
        return ResponseEntity.ok(Map.of("message", "Artist info updated successfully."));
    }

    @GetMapping("/spotify")
    public ResponseEntity<ArtistSharesDto> getArtistFromSpotify(@RequestParam String link) {
        logger.info("Fetching artist details from Spotify for link: {}", link);
        if (link == null || link.trim().isEmpty()) {
            logger.warn("Spotify link is empty or null");
            return ResponseEntity.badRequest().body(null);
        }
        try {
            String artistId = extractArtistIdFromSpotifyLink(link);
            if (artistId == null || artistId.trim().isEmpty()) {
                logger.warn("Could not extract artist ID from link: {}", link);
                return ResponseEntity.badRequest().body(null);
            }
            logger.info("Extracted artist ID: {}", artistId);
            Optional<Artist> artistOptional = artistService.getArtistById(artistId);
            if (artistOptional.isPresent()) {
                logger.info("Artist found: {} - {}", artistId, artistOptional.get().getName());
                return ResponseEntity.ok(ArtistSharesDto.fromArtist(artistOptional.get()));
            } else {
                logger.warn("Artist not found for ID: {}", artistId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error fetching artist from Spotify for link: {}. Exception: {}", link, e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @PutMapping("/{spotifyId}/contract")
    public ResponseEntity<?> updateContractAddress(
            @PathVariable String spotifyId,
            @RequestBody Map<String, String> body) {

        String contractAddress = body.get("contractAddress");

        if (contractAddress == null || !contractAddress.matches("^(0x)?[a-fA-F0-9]{40}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid contract address"));
        }

        if (!contractAddress.startsWith("0x")) {
            contractAddress = "0x" + contractAddress;
        }

        try {
            contractAddress = contractAddress.toLowerCase();

            artistService.updateContractAddress(spotifyId, contractAddress);
            logger.info("Contract address updated successfully for artist ID: {}", spotifyId);

            // This will now work!
            contractService.evictContractAddressCache(spotifyId);

            blockchainSyncService.subscribeToNewContract(contractAddress);
            logger.info("Subscribed to events for new contract: {}", contractAddress);

            return ResponseEntity.ok(Map.of("success", true, "contractAddress", contractAddress));
        } catch (Exception e) {
            logger.error("Failed to update contract address for artist ID: " + spotifyId, e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/artistData")
    public ResponseEntity<ArtistSharesDto> getArtistData(@RequestParam String artistId) {
        logger.info("Fetching artist data for ID: {}", artistId);
        Optional<Artist> artistOptional = artistService.getArtistById(artistId);
        if (artistOptional.isPresent()) {
            return ResponseEntity.ok(ArtistSharesDto.fromArtist(artistOptional.get()));
        }
        logger.warn("Artist not found for ID: {}", artistId);
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/candleData")
    public ResponseEntity<List<CandleData>> getCandleData(
            @RequestParam String artistId,
            @RequestParam String timeframe) {
        if (artistId == null || timeframe == null) {
            return ResponseEntity.badRequest().body(null);
        }
        logger.info("Fetching candle data for artist ID: {}, timeframe: {}", artistId, timeframe);
        try {
            Timeframe tf = Timeframe.fromValue(timeframe.toUpperCase());
            logger.info("Parsed timeframe: {}", tf);
            List<CandleData> candleData = candleDataService.getCandleDataByArtistIdAndTimeframe(artistId, tf);
            
            // DEBUG: Log RAW precision
            candleData.forEach(candle -> {
                String spread = candle.getHigh().subtract(candle.getLow()).toPlainString();
                logger.info("Candle OHLC RAW: O={}, H={}, L={}, C={}, SPREAD={}, VOL={}", 
                    candle.getOpen().toPlainString(),
                    candle.getHigh().toPlainString(),
                    candle.getLow().toPlainString(),
                    candle.getClose().toPlainString(),
                    spread,
                    candle.getVolume().toPlainString()
                );
            });
            
            logger.info("Returning {} candles for artistId={}, timeframe={}", candleData.size(), artistId, timeframe);
            return ResponseEntity.ok(candleData);
        } catch (Exception e) {
            logger.error("Error fetching candle data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    private String extractArtistIdFromSpotifyLink(String link) {
        logger.info("Extracting artist ID from Spotify link: {}", link);
        if (link == null || link.trim().isEmpty()) {
            logger.warn("Spotify link is null or empty");
            return null;
        }

        String cleanedLink = link.trim();
        try {
            if (!cleanedLink.contains("open.spotify.com/artist/")) {
                logger.warn("Invalid Spotify link format: {}", cleanedLink);
                return null;
            }

            String[] parts = cleanedLink.split("/artist/");
            if (parts.length < 2) {
                logger.warn("Could not split link to extract artist ID: {}", cleanedLink);
                return null;
            }

            String artistId = parts[1].split("[?\\s]")[0];
            if (artistId.isEmpty()) {
                logger.warn("Extracted artist ID is empty: {}", cleanedLink);
                return null;
            }

            logger.info("Extracted artist ID: {}", artistId);
            return artistId;
        } catch (Exception e) {
            logger.error("Error parsing Spotify link: {}. Exception: {}", cleanedLink, e.getMessage(), e);
            return null;
        }
    }
}