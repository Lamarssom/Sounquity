package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "trade_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TradeOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sellerId;

    @Column(nullable = false)
    private String artistId;

    @Column(nullable = false)
    private int shares;

    @Column(nullable = false)
    private BigDecimal askingPrice;

    @Column(nullable = false)
    private boolean isActive = true;

    public TradeOffer(String sellerId, String artistId, int shares, BigDecimal askingPrice) {
        this.sellerId = sellerId;
        this.artistId = artistId;
        this.shares = shares;
        this.askingPrice = askingPrice;
        this.isActive = true;
    }
}
