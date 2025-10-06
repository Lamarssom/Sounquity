package com.musicinvestment.musicapp.model;

import lombok.Data;
import java.time.LocalDateTime

@Data
public class Financials {
    private String currentPrice; // USD formatted
    private String volume24h; // USD formatted
    private String marketCap; // USD formatted
    private String dailyLiquidity; // USD value of dailyLimit
    private String LiquidityPercentage; // Remaining percentage
    private long availableSupply; // Tradeable shares 
    private LocalDateTime nextReset; // Time untol next reset

    // Add constructor
    public Financials(String currentPrice, String volume24h, String marketCap,
                      double dailyLiquidity, double liquidityPercentage,
                      long availableSupply, LocalDateTime nextReset) {
        this.currentPrice = currentPrice;
        this.volume24h = volume24h;
        this.marketCap = marketCap;
        this.dailyiquidity = dailyLiquidity; 
        this.availableSupply = availableSupply;
        this.nextReset = nextReset;
    }

}
