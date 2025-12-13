package com.musicinvestment.musicapp.util;

import com.musicinvestment.musicapp.model.TradingSession;
import com.musicinvestment.musicapp.repository.TradingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Component
public class TradingHoursUtil {

    private final TradingSessionRepository tradingSessionRepository;

    @Autowired
    public TradingHoursUtil(TradingSessionRepository tradingSessionRepository) {
        this.tradingSessionRepository = tradingSessionRepository;
    }

    public boolean isWithinTradingHours() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();

        // Retrieve the trading session for today
        String dayOfWeek = day.name().toLowerCase(); // Convert to lowercase to match DB values
        Optional<TradingSession> tradingSession = tradingSessionRepository.findByDayOfWeekIgnoreCase(dayOfWeek);

        if (tradingSession.isPresent()) {
            LocalTime openTime = tradingSession.get().getStartTime();
            LocalTime closeTime = tradingSession.get().getEndTime();

            return !currentTime.isBefore(openTime) && !currentTime.isAfter(closeTime);
        } else {
            // No trading session found for the day, treat it as outside trading hours
            return false;
        }
    }
}