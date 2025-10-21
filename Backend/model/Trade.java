package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.math.BigInteger;
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
    private BigInteger amount;

    @Column(nullable = false)
    private BigInteger price;

    @Column(name = "eth_value", nullable = false)
    private BigInteger ethValue;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "timestamp_string")
    private String timestampString;

    @Column(name = "buyer_or_seller", nullable = false)
    private String buyerOrSeller;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "amount_in_usd", precision = 20, scale = 10)
    private BigDecimal amountInUsd;

    @Column(name = "price_in_usd", precision = 20, scale = 10)
    private BigDecimal priceInUsd;

    public enum EventType {
        BUY, SELL
    }

    // Validation in setters
    public void setAmount(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative or null");
        }
        this.amount = amount;
    }

    public void setEthValue(BigInteger ethValue) {
        if (ethValue == null || ethValue.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("EthValue cannot be negative or null");
        }
        this.ethValue = ethValue;
    }
}
