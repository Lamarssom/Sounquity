package com.musicinvestment.musicapp.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
public class Financials {
    private String currentPrice; // USD formatted
    private String volume24h; // USD formatted
    private String marketCap; // USD formatted
    private double dailyLiquidity; // USD value of dailyLimit
    private double liquidityPercentage; // Remaining percentage
    private double ethLiquidityInCurveUsd;
    private long availableSupply; // Tradable shares
    private String nextReset; // Time until next reset

    // Update constructor
    public Financials(String currentPrice, String volume24h, String marketCap, 
                      double dailyLiquidity, double liquidityPercentage, 
                      long availableSupply, LocalDateTime nextReset, double ethLiquidityInCurveUsd) {
        this.currentPrice = currentPrice;
        this.volume24h = volume24h;
        this.marketCap = marketCap;
        this.dailyLiquidity = dailyLiquidity;
        this.liquidityPercentage = liquidityPercentage;
        this.ethLiquidityInCurveUsd = ethLiquidityInCurveUsd;
        this.availableSupply = availableSupply;
        this.nextReset = nextReset != null
            ? nextReset.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE_TIME)
            : null;
    }
}