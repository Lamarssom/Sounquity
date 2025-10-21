package com.musicinvestment.musicapp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "candle_data")
public class CandleData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @NotNull
    @Convert(converter = TimeframeConverter.class)
    @Column(nullable = false, columnDefinition = "ENUM('1m','5m','15m','30m','1H','4H','1D','1W')")
    private Timeframe timeframe;

    @NotNull
    @Column(nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal open;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal high;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal low;

    @NotNull
    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal close;

    @NotNull
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_event_type", nullable = true)
    private Trade.EventType lastEventType;

    // Constructors
    public CandleData() {}

    public CandleData(String artistId, Timeframe timeframe, LocalDateTime timestamp,
                      BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        this.artistId = artistId;
        this.timeframe = timeframe;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Getters and Setters
    public Trade.EventType getLastEventType() { return lastEventType; }
    public void setLastEventType(Trade.EventType lastEventType) { this.lastEventType = lastEventType; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }
    public Timeframe getTimeframe() { return timeframe; }
    public void setTimeframe(Timeframe timeframe) { this.timeframe = timeframe; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
}
