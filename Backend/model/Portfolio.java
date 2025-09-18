package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    private String userId;
    private String artistName;
    private int sharesOwned;

    // Constructors
    public Portfolio() {}

    public Portfolio(String userId, String artistName, int sharesOwned) {
        this.userId = userId;
        this.artistName = artistName;
        this.sharesOwned = sharesOwned;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getArtistName() { return artistName; }
    public int getSharesOwned() { return sharesOwned; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setArtistName(String artistName) { this.artistName = artistName; }
    public void setSharesOwned(int sharesOwned) { this.sharesOwned = sharesOwned; }
}
