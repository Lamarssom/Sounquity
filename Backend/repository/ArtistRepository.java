package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, String> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO artists (id, name, spotify_url, followers, popularity, image_url, contract_address) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)", nativeQuery = true)
    void insertArtist(String id, String name, String spotifyUrl, int followers, int popularity, String imageUrl, String contractAddress);

    Optional<Artist> findById(String id);

    Optional<Artist> findByContractAddress(String contractAddress);

    @Query(value = "SELECT * FROM artists WHERE name ILIKE %?1% LIMIT 1", nativeQuery = true)
    Optional<Artist> findByName(String name);

    @Query(value = "SELECT * FROM artists WHERE LOWER(name) LIKE LOWER(CONCAT('%', ?1, '%'))", nativeQuery = true)
    List<Artist> findByNameContainingIgnoreCase(String name);

    @Query("SELECT a FROM Artist a")
    List<Artist> findAllArtists();

    @Modifying
    @Transactional
    @Query("UPDATE Artist a SET a.contractAddress = ?2 WHERE a.id = ?1")
    void updateContractAddress(String artistId, String contractAddress);

    @Query("SELECT a.contractAddress FROM Artist a WHERE a.id = ?1")
    Optional<String> findContractAddressByArtistId(String artistId);

    @Query("SELECT a.id FROM Artist a WHERE a.contractAddress = ?1")
    Optional<String> findArtistIdByContractAddress(String contractAddress);

    @Query("SELECT a.contractAddress FROM Artist a WHERE a.contractAddress IS NOT NULL")
    List<String> findAllContractAddresses();
}