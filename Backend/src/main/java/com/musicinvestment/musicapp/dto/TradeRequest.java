package com.musicinvestment.musicapp.dto;

import java.math.BigDecimal;

public class TradeRequest {
    private String userId;
    private String artistId;
    private Integer shares; // Changed to Integer to handle potential null values gracefully
    private BigDecimal price;
    private String offerId;

    // Default constructor (needed for JSON deserialization)
    public TradeRequest() {}

    // Constructor with parameters
    public TradeRequest(String userId, String artistId, Integer shares, BigDecimal price, String offerId) {
        this.userId = userId;
        this.artistId = artistId;
        this.shares = shares;
        this.price = price;
        this.offerId = offerId;
    }

    // Getters & Setters
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

    public Integer getShares() {
        return shares;
    }

    public void setShares(Integer shares) {
        this.shares = shares;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    // ToString method for debugging purposes
    @Override
    public String toString() {
        return "TradeRequest{" +
                "userId=" + userId +
                ", artistId='" + artistId + '\'' +
                ", shares=" + shares +
                ", price=" + price +
                ", offerId=" + offerId +
                '}';
    }
}