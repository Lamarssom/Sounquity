package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    @Query("SELECT SUM(t.ethValue) FROM Trade t WHERE t.artistId = :artistId AND t.timestamp > :since")
    BigInteger sumEthValueLast24h(String artistId, LocalDateTime since);
}