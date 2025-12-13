package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.Airdrop;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.repository.AirdropRepository;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import com.musicinvestment.musicapp.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AirdropService {

    private static final Logger logger = LoggerFactory.getLogger(AirdropService.class);

    private final AirdropRepository airdropRepository;
    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;

    @Autowired
    public AirdropService(AirdropRepository airdropRepository, UserRepository userRepository, ArtistRepository artistRepository) {
        this.airdropRepository = airdropRepository;
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
    }

    @Transactional
    public void allocateAirdrop(String userId, List<String> favoriteArtistIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with ID " + userId + " not found"));

        for (String artistId : favoriteArtistIds) {
            Artist artist = artistRepository.findById(artistId)
                    .orElseThrow(() -> new RuntimeException("Artist with ID " + artistId + " not found"));

            if (airdropRepository.existsByUserAndArtist(user, artist)) {
                logger.info("Airdrop already exists for User ID {} and Artist ID {}", userId, artistId);
                continue;
            }

            int shares = calculateShares(artist);

            Airdrop airdrop = new Airdrop(user, artist, shares);
            airdropRepository.save(airdrop);
            logger.info("Airdrop allocated: User ID {} received {} shares for Artist ID {}", userId, shares, artistId);
        }
    }

    private int calculateShares(Artist artist) {
        Integer popularity = artist.getPopularity();
        return (popularity != null && popularity < 50) ? 100 : 50;
    }
}