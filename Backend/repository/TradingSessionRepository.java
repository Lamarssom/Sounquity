package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.TradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradingSessionRepository extends JpaRepository<TradingSession, Long> {
    Optional<TradingSession> findByDayOfWeekIgnoreCase(String dayOfWeek);
}