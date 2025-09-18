package com.musicinvestment.musicapp.model;

import lombok.Data;

@Data
public class Financials {
    private String currentPrice; // USD formatted
    private String volume24h; // USD formatted
    private String marketCap; // USD formatted

    // Add constructor
    public Financials(String currentPrice, String volume24h, String marketCap) {
        this.currentPrice = currentPrice;
        this.volume24h = volume24h;
        this.marketCap = marketCap;
    }
}