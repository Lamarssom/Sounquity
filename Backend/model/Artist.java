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
    private String id; // This is now the Spotify artist ID

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

    @Column(nullable = true)
    private Double price;

    @Column(nullable = true)
    private Double volume;

    @Column(nullable = true)
    private String contractAddress; // Used for smart contract interaction

    @Column(length = 1000)
    private String description;

    @Column(nullable = true)
    private Double currentPrice;

    @Column(nullable = true)
    private Double priceChangePercent;

    @Column(nullable = true)
    private Integer totalVolume;

    @Column(nullable = true)
    private Integer availableSupply;

    @Column(nullable = true)
    private Integer airdropSupply;

    @Column(name = "daily_liquidity", nullable = true)
    private BigDecimal dailyLiquidity;

    @Column(name = "curve_complete", nullable = true)
    private Boolean curveComplete;

    // Constructor without description
    public Artist(String id, String name, String spotifyUrl, int followers, int popularity, String imageUrl, String contractAddress) {
        this.id = id;
        this.name = name;
        this.spotifyUrl = spotifyUrl;
        this.followers = followers;
        this.popularity = popularity;
        this.imageUrl = imageUrl;
        this.contractAddress = contractAddress;
    }

    // Constructor with description
    public Artist(String id, String name, String spotifyUrl, int followers, int popularity, String imageUrl, String contractAddress, String description) {
        this.id = id;
        this.name = name;
        this.spotifyUrl = spotifyUrl;
        this.followers = followers;
        this.popularity = popularity;
        this.imageUrl = imageUrl;
        this.contractAddress = contractAddress;
        this.description = description;
    }
}
