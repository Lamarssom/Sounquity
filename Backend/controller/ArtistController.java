package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.service.ArtistService;
import com.musicinvestment.musicapp.service.CandleDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private static final Logger logger = LoggerFactory.getLogger(ArtistController.class);

    private final ArtistService artistService;
    private final CandleDataService candleDataService;

    @Autowired
    public ArtistController(ArtistService artistService, CandleDataService candleDataService) {
        this.artistService = artistService;
        this.candleDataService = candleDataService;
        logger.info("ArtistController initialized");
    }

    @GetMapping
    public List<ArtistSharesDto> getAllArtists() {
        logger.info("Fetching all artists");
        List<Artist> artists = artistService.getAllArtists();
        logger.info("Fetched artists count: {}", artists.size());
        return artists.stream()
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
    public ArtistSharesDto getArtistByContractAddress(@PathVariable String contractAddress) {
        logger.info("Fetching artist by contract address: {}", contractAddress);
        Optional<Artist> artistOptional = artistService.getArtistByContractAddress(contractAddress);
        if (artistOptional.isPresent()) {
            return ArtistSharesDto.fromArtist(artistOptional.get());
        } else {
            logger.warn("Artist not found for contract address: {}", contractAddress);
            throw new RuntimeException("Artist not found for contract: " + contractAddress);
        }
    }

    @GetMapping("/test")
    public String testEndpoint() {
        logger.info("Test endpoint accessed");
        return "Artist API is working!";
    }

    @PutMapping("/{id}/update-contract")
    public ResponseEntity<Map<String, String>> updateContractAddress(
            @PathVariable String id,
            @RequestBody Map<String, String> requestBody) {
        String contractAddress = requestBody.get("contractAddress");
        logger.info("Updating contract for artist ID: {} with contract address: {}", id, contractAddress);
        if (contractAddress == null || contractAddress.isEmpty()) {
            logger.warn("Contract address is empty for artist ID: {}", id);
            throw new IllegalArgumentException("Contract address must not be empty");
        }
        artistService.updateContractAddress(id, contractAddress);
        return ResponseEntity.ok(Map.of("message", "Contract address updated successfully."));
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
            @RequestParam(required = false) String timeframe) {
        logger.info("Fetching candle data for artist ID: {}, timeframe: {}", artistId, timeframe);
        try {
            List<CandleData> candleData;
            if (timeframe != null && !timeframe.isEmpty()) {
                Timeframe tf = Timeframe.fromValue(timeframe);
                candleData = candleDataService.getCandleDataByArtistIdAndTimeframe(artistId, tf);
            } else {
                candleData = candleDataService.getCandleDataByArtistId(artistId);
            }
            candleData = aggregateCandleData(candleData, timeframe);
            return ResponseEntity.ok(candleData);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid timeframe for artist ID {}: {}", artistId, e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error fetching candle data for artist ID {}: {}", artistId, e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    private List<CandleData> aggregateCandleData(List<CandleData> candles, String timeframe) {
        if (candles == null || candles.isEmpty() || timeframe == null || timeframe.isEmpty()) {
            return candles;
        }

        long intervalSeconds;
        switch (timeframe) {
            case "1m":
                intervalSeconds = 60;
                break;
            case "5m":
                intervalSeconds = 300;
                break;
            case "15m":
                intervalSeconds = 900;
                break;
            case "1H":
                intervalSeconds = 3600;
                break;
            case "4H":
                intervalSeconds = 14400;
                break;
            case "1D":
                intervalSeconds = 86400;
                break;
            default:
                return candles;
        }

        Map<Long, List<CandleData>> groupedByTime = candles.stream()
                .collect(Collectors.groupingBy(
                        candle -> {
                            long timestampSeconds = candle.getTimestamp().toEpochSecond(java.time.ZoneOffset.UTC);
                            return timestampSeconds - (timestampSeconds % intervalSeconds);
                        }));

        return groupedByTime.entrySet().stream()
                .map(entry -> {
                    List<CandleData> group = entry.getValue();
                    CandleData first = group.get(0);
                    CandleData aggregated = new CandleData();
                    aggregated.setArtistId(first.getArtistId());
                    aggregated.setTimeframe(Timeframe.fromValue(timeframe));
                    aggregated.setTimestamp(LocalDateTime.ofEpochSecond(entry.getKey(), 0, java.time.ZoneOffset.UTC));

                    BigDecimal open = first.getOpen();
                    BigDecimal high = group.stream()
                            .map(CandleData::getHigh)
                            .max(BigDecimal::compareTo)
                            .orElse(first.getHigh());
                    BigDecimal low = group.stream()
                            .map(CandleData::getLow)
                            .min(BigDecimal::compareTo)
                            .orElse(first.getLow());
                    BigDecimal close = group.get(group.size() - 1).getClose();
                    BigDecimal volume = group.stream()
                            .map(CandleData::getVolume)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    aggregated.setOpen(open);
                    aggregated.setHigh(high);
                    aggregated.setLow(low);
                    aggregated.setClose(close);
                    aggregated.setVolume(volume);

                    return aggregated;
                })
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .collect(Collectors.toList());
    }

    private String extractArtistIdFromSpotifyLink(String link) {
        logger.info("Extracting artist ID from Spotify link: {}", link);
        if (link == null || link.trim().isEmpty()) {
            logger.warn("Spotify link is null or empty");
            return null;
        }

        // Trim any trailing or leading spaces
        String cleanedLink = link.trim();
        try {
            // Check if the link matches the expected Spotify artist URL pattern
            if (!cleanedLink.contains("open.spotify.com/artist/")) {
                logger.warn("Invalid Spotify link format: {}", cleanedLink);
                return null;
            }

            // Extract the artist ID using string manipulation
            String[] parts = cleanedLink.split("/artist/");
            if (parts.length < 2) {
                logger.warn("Could not split link to extract artist ID: {}", cleanedLink);
                return null;
            }

            // Get the part after /artist/ and remove any query parameters or trailing characters
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