package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "trade_history")
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Column(name = "shares", nullable = false)
    private int shares;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "trade_time", nullable = false, updatable = false)
    private Timestamp tradeTime;

    // Constructors
    public TradeHistory() {}

    public TradeHistory(String userId, String artistId, TradeType tradeType, int shares, BigDecimal price, Timestamp tradeTime) {
        this.userId = userId;
        this.artistId = artistId;
        this.tradeType = tradeType;
        this.shares = shares;
        this.price = price;
        this.tradeTime = tradeTime;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getArtistId() {
        return artistId;
    }

    public TradeType getTradeType() {
        return tradeType;
    }

    public int getShares() {
        return shares;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Timestamp getTradeTime() {
        return tradeTime;
    }

    // Setters
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public void setTradeType(TradeType tradeType) {
        this.tradeType = tradeType;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setTradeTime(Timestamp tradeTime) {
        this.tradeTime = tradeTime;
    }
}