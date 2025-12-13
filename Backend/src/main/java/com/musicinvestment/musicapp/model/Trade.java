package com.musicinvestment.musicapp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @Column(name = "contract_address", nullable = false)
    private String contractAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(nullable = false)
    private String amount;

    @Column(nullable = false)
    private String price;

    @Column(name = "eth_value", nullable = false)
    private String ethValue;

    @Column(name = "buyer_or_seller", nullable = false)
    private String buyerOrSeller;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "amount_in_usd", precision = 20, scale = 10)
    private BigDecimal amountInUsd;

    @Column(name = "price_in_usd", precision = 20, scale = 10)
    private BigDecimal priceInUsd;

    // THIS IS THE ONLY TIMESTAMP FIELD
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public enum EventType {
        BUY, SELL
    }
}