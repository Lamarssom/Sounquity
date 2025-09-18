package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

import java.util.List;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, String> {
    List<TradeHistory> findByUserId(String userId);
    List<TradeHistory> findByArtistId(String artistId);
    List<TradeHistory> findByUserIdAndArtistId(String userId, String artistId);
    List<TradeHistory> findByUserIdAndTradeTime(String userId, Timestamp tradeTime);
}
