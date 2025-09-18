package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.UserShares;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSharesRepository extends JpaRepository<UserShares, String> {
    Optional<UserShares> findByUserIdAndArtistId(String userId, String artistId);
}
