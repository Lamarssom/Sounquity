package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_shares")
public class UserShares {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @Column(nullable = false)
    private int shares;

    @Column(name = "is_listed_for_sale", nullable = false)
    private boolean isListedForSale = false; // Default to false

    @Column(name = "asking_price")
    private BigDecimal askingPrice; // Can be null if not listed

    public UserShares() {
    }

    public UserShares(String userId, String artistId, int shares) {
        this.userId = userId;
        this.artistId = artistId;
        this.shares = shares;
        this.isListedForSale = false;
        this.askingPrice = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public boolean isListedForSale() {
        return isListedForSale;
    }

    public void setListedForSale(boolean listedForSale) {
        isListedForSale = listedForSale;
    }

    public BigDecimal getAskingPrice() {
        return askingPrice;
    }

    public void setAskingPrice(BigDecimal askingPrice) {
        this.askingPrice = askingPrice;
    }
}