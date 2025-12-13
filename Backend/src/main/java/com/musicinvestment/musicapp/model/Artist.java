package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "artists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Artist {

    @Id
    private String id; // Spotify artist ID

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String spotifyUrl;

    @Column(nullable = true)
    private String imageUrl;

    private int followers;
    private int popularity;

    @ManyToMany(mappedBy = "favoriteArtists")
    @JsonIgnore
    private Set<User> users = new HashSet<>();

    @Column(nullable = true)
    private String symbol;

    // ---------- MONEY FIELDS (BigDecimal) ----------
    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal price;          // current token price in USD

    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal volume;         // 24h volume in USD

    @Column(nullable = true, precision = 20, scale = 8)
    private BigDecimal currentPrice;   // live price (same as price but kept for UI)

    @Column(nullable = true, precision = 20, scale = 2)
    private BigDecimal priceChangePercent; // % change (2 decimals is enough)

    // ---------- INTEGER FIELDS ----------
    @Column(nullable = true)
    private Integer totalVolume;

    @Column(nullable = true)
    private Integer availableSupply;

    @Column(nullable = true)
    private Integer airdropSupply;

    @Column(name = "daily_liquidity", nullable = true, precision = 20, scale = 8)
    private BigDecimal dailyLiquidity;

    @Column(name = "curve_complete", nullable = true)
    private Boolean curveComplete;

    // ---------- OTHER ----------
    @Column(nullable = true)
    private String contractAddress;

    @Column(length = 1000)
    private String description;

    /* Constructors (keep the ones you already use)                       */
    public Artist(String id, String name, String spotifyUrl,
                  int followers, int popularity,
                  String imageUrl, String contractAddress) {
        this.id = id;
        this.name = name;
        this.spotifyUrl = spotifyUrl;
        this.followers = followers;
        this.popularity = popularity;
        this.imageUrl = imageUrl;
        this.contractAddress = contractAddress;
    }

    public Artist(String id, String name, String spotifyUrl,
                  int followers, int popularity,
                  String imageUrl, String contractAddress,
                  String description) {
        this(id, name, spotifyUrl, followers, popularity, imageUrl, contractAddress);
        this.description = description;
    }


    public String getSpotifyId() {
        return this.id;
    }
}