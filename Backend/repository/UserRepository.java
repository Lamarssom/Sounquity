package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    @Query ("SELECT u FROM User u Left JOIN FETCH u.favoriteArtists WHERE u.id = :id")
    Optional<User> findByIdWithFavoriteArtists(String id);
    Optional<User> findById(String id);
    boolean existsById(String id);
}
