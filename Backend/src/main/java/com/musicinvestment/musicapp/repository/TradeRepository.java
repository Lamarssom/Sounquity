package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("""
    SELECT COALESCE(SUM(CAST(t.ethValue AS bigdecimal)), 0)
    FROM Trade t
    WHERE t.artistId = :artistId AND t.timestamp > :since
    """)
    BigDecimal sumEthValueLast24h(@Param("artistId") String artistId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amountInUsd), 0) FROM Trade t WHERE LOWER(t.buyerOrSeller) = LOWER(:userAddress) AND t.timestamp >= :since")
    BigDecimal sumAmountInUsdLast24h(@Param("userAddress") String userAddress, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amountInUsd), 0) FROM Trade t WHERE t.artistId = :artistId AND t.timestamp > :cutoff")
    BigDecimal sumVolume24hUsd(@Param("artistId") String artistId, @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.artistId = :artistId")
    long countByArtistId(String artistId);

    Optional<Trade> findByTxHash(String txHash);

    List<Trade> findByArtistIdOrderByTimestampAsc(String artistId);

    Optional<Trade> findTopByArtistIdOrderByTimestampDesc(String artistId);
}