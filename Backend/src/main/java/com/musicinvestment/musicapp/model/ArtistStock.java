package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "artist_stock")
public class ArtistStock {

    @Id
    @Column(name = "artist_id", nullable = false, unique = true)
    private String artistId;

    @Column(name = "artist_name")
    private String artistName;

    @Column(name = "stock_price")
    private BigDecimal stockPrice;

    @Column(name = "total_shares")
    private int totalShares;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @Column(name = "platform_buy_price")
    private BigDecimal platformBuyPrice;

    @Column(name = "platform_sell_price")
    private BigDecimal platformSellPrice;

    @Column(name = "trending_score")
    private int trendingScore;

    @Column(name = "timestamp")
    private Timestamp timestamp; // Added timestamp for tracking when the price was updated

    // Default constructor
    public ArtistStock() {
    }

    // Constructor with all fields
    public ArtistStock(String artistId, String artistName, BigDecimal stockPrice, int totalShares, BigDecimal currentPrice, BigDecimal platformBuyPrice, BigDecimal platformSellPrice, int trendingScore, Timestamp timestamp) {
        this.artistId = artistId;
        this.artistName = artistName;
        this.stockPrice = stockPrice;
        this.totalShares = totalShares;
        this.currentPrice = currentPrice;
        this.platformBuyPrice = platformBuyPrice;
        this.platformSellPrice = platformSellPrice;
        this.trendingScore = trendingScore;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public BigDecimal getStockPrice() {
        return stockPrice;
    }

    public void setStockPrice(BigDecimal stockPrice) {
        this.stockPrice = stockPrice;
    }

    public int getTotalShares() {
        return totalShares;
    }

    public void setTotalShares(int totalShares) {
        this.totalShares = totalShares;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getPlatformBuyPrice() {
        return platformBuyPrice;
    }

    public void setPlatformBuyPrice(BigDecimal platformBuyPrice) {
        this.platformBuyPrice = platformBuyPrice;
    }

    public BigDecimal getPlatformSellPrice() {
        return platformSellPrice;
    }

    public void setPlatformSellPrice(BigDecimal platformSellPrice) {
        this.platformSellPrice = platformSellPrice;
    }

    public int getTrendingScore() {
        return trendingScore;
    }

    public void setTrendingScore(int trendingScore) {
        this.trendingScore = trendingScore;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    // Method to get the price for a specific artist on a specific date (start of day)
    public static Timestamp getStartOfDayTimestamp() {
        long currentTimeMillis = System.currentTimeMillis();
        Timestamp timestamp = new Timestamp(currentTimeMillis);
        return new Timestamp(timestamp.getTime() - timestamp.getTime() % (24 * 60 * 60 * 1000)); // Set to midnight
    }
}