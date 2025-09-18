package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.ShareOwnership;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareOwnershipRepository extends JpaRepository<ShareOwnership, String> {
    Optional<ShareOwnership> findByUserAndArtist(User user, Artist artist);
    List<ShareOwnership> findByUserId(String userId); // Added method
}