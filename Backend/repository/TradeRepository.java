package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.param;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    @Query("SELECT SUM(t.ethValue) FROM Trade t WHERE t.artistId = :artistId AND t.timestamp > :since")
    BigInteger sumEthValueLast24h(String artistId, LocalDateTime since);

    @Query("SElECT COUNT(t) FROM Trade t WHERE t.artistId = :artistId")
    long countByArtistId(String artistId);

    Optional<Trade> findByTxHash(String txHash);

    List<Trade> findByArtistIdOrderByTimestampAsc(String artistId);

}
