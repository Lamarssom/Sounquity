package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Airdrop;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AirdropRepository extends JpaRepository<Airdrop, Long> {
    
    List<Airdrop> findByUser(User user); // Fetch airdrop details for a specific user
    
    boolean existsByUserAndArtist(User user, Artist artist); // Check if airdrop already exists
}
