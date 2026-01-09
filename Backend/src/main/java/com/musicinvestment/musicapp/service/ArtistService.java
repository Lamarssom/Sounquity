package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.dto.ArtistSharesDto;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class ArtistService {

    private static final Logger logger = LoggerFactory.getLogger(ArtistService.class);

    private final ArtistRepository artistRepository;
    private final SpotifyService spotifyService;

    @Autowired
    public ArtistService(ArtistRepository artistRepository, SpotifyService spotifyService) {
        this.artistRepository = artistRepository;
        this.spotifyService = spotifyService;
    }

    public Optional<Artist> getArtistById(String id) {
        logger.info("Received ID to fetch artist: {}", id);

        Optional<Artist> existingArtist = artistRepository.findById(id);
        if (existingArtist.isPresent()) {
            logger.info("Artist found by ID: {}", existingArtist.get().getName());
            return existingArtist;
        }

        Optional<Artist> byContract = artistRepository.findByContractAddress(id);
        if (byContract.isPresent()) {
            logger.info("Artist found by contract address: {}", byContract.get().getName());
            return byContract;
        }

        logger.info("Artist not found locally. Attempting Spotify fetch for ID: {}", id);
        try {
            Artist artist = spotifyService.getArtistById(id);
            if (artist == null) {
                logger.warn("Artist not found on Spotify: {}", id);
                return Optional.empty();
            }
            logger.info("Artist fetched from Spotify: {} - {}", artist.getId(), artist.getName());
            return Optional.of(artistRepository.save(artist));
        } catch (Exception e) {
            logger.error("Failed to fetch artist from Spotify: {}", id, e);
            return Optional.empty();
        }
    }

    @Transactional
    public void saveArtistWithLogging(String id, String name, String spotifyUrl, int followers, int popularity, String imageUrl, String contractAddress) {
        logger.info("Manually inserting artist: {} - {}", id, name);
        Artist artist = new Artist(id, name, spotifyUrl, followers, popularity, imageUrl, contractAddress, id);
        artistRepository.save(artist);
    }

    public List<Artist> searchArtistsByName(String name) {
        logger.info("Searching for artists with name containing: {}", name);
        List<Artist> artists = artistRepository.findByNameContainingIgnoreCase(name);

        if (artists.isEmpty()) {
            logger.info("No local artists found. Searching Spotify for: {}", name);
            List<Artist> spotifyArtists = spotifyService.searchArtistsByName(name);

            for (Artist artist : spotifyArtists) {
                if (!artistRepository.existsById(artist.getId())) {
                    logger.info("Saving new artist from Spotify: {}", artist.getName());
                    artistRepository.save(artist);
                }
            }
            artists.addAll(spotifyArtists);
        }

        return artists;
    }

    public List<Artist> getAllArtists() {
        logger.info("Fetching all artists from the database");
        return artistRepository.findAll();
    }

    public Optional<String> getArtistContractAddress(String artistId) {
        logger.info("Fetching contract address for artist: {}", artistId);
        return artistRepository.findContractAddressByArtistId(artistId);
    }

    public Optional<Artist> getArtistByContractAddress(String contractAddress) {
        logger.info("Fetching artist by contract address: {}", contractAddress);
        return artistRepository.findByContractAddressIgnoreCase(contractAddress);
    }

    @Transactional
    public void updateContractAddress(String artistId, String contractAddress) {
        logger.info("Updating contract address for artist with ID: {}", artistId);

        if (contractAddress == null || contractAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Contract address must not be empty or null.");
        }

        contractAddress = contractAddress.toLowerCase();

        Artist artist = artistRepository.findById(artistId)
            .orElseThrow(() -> new RuntimeException("Artist not found with ID: " + artistId));

        if (contractAddress.equals(artist.getContractAddress())) {
            logger.info("Contract address is the same as the current one. No update needed.");
            return;
        }

        artist.setContractAddress(contractAddress);
        artistRepository.save(artist);

        logger.info("Contract address updated successfully for artist: {} ({})", artist.getName(), contractAddress);
    }

    @Transactional
    public void updateArtistInfo(String id, ArtistSharesDto artistSharesDto) {
        logger.info("Updating artist info for ID: {}", artistSharesDto.getArtistId());

        Artist artist = artistRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Artist not found with ID: " + id));

        artist.setName(artistSharesDto.getArtistName());
        artist.setSpotifyUrl(artistSharesDto.getSpotifyUrl());
        artist.setImageUrl(artistSharesDto.getImageUrl());
        artist.setFollowers(artistSharesDto.getFollowers());
        artist.setPopularity(artistSharesDto.getPopularity());

        String existingContractAddress = artist.getContractAddress();
        String newContractAddress = artistSharesDto.getContractAddress();

        if ((existingContractAddress == null || existingContractAddress.isEmpty())
                && newContractAddress != null && !newContractAddress.isEmpty()) {
            artist.setContractAddress(newContractAddress);
            logger.info("Contract address set for the first time: {}", newContractAddress);
        } else if (newContractAddress != null && !newContractAddress.equals(existingContractAddress)) {
            logger.warn("Attempt to overwrite existing contract address ignored. Existing: {}, New: {}",
                existingContractAddress, newContractAddress);
        }

        artist.setCurrentPrice(artistSharesDto.getCurrentPrice());
        artist.setPriceChangePercent(artistSharesDto.getPriceChangePercent());
        artist.setTotalVolume(artistSharesDto.getTotalVolume());
        artist.setAvailableSupply(artistSharesDto.getAvailableSupply());
        artist.setAirdropSupply(artistSharesDto.getAirdropSupply());

        artistRepository.save(artist);

        logger.info("Artist info updated successfully: {}", artist.getName());
    }
}