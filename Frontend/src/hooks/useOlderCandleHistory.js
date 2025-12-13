import { useEffect, useState, useCallback } from "react";
import axios from "axios";
import logger from "../utilities/logger"; // Import logger

const TIMEFRAME_MAP = {
  "1m": 60,
  "5m": 300,
  "15m": 900,
  "1h": 3600,
  "4h": 14400,
  "1D": 86400,
};

export const useOlderCandleHistory = (artistId, timeframe = "5m") => {
  const [candleData, setCandleData] = useState([]);
  const [volumeData, setVolumeData] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchCandleData = useCallback(async () => {
    setLoading(true);
    try {
      const interval = TIMEFRAME_MAP[timeframe] || 300;
      //logger.debug("useOlderCandleHistory", "Fetching older candles for timeframe", timeframe, (${interval}s interval));
      const response = await axios.get(
        `${import.meta.env.VITE_API_URL}/api/artists/candleData?artistId=${artistId}&timeframe=${timeframe}`
      );
      const rawCandleData = Array.isArray(response.data) ? response.data : [];
      //logger.debug("useOlderCandleHistory", "Raw older candle data:", rawCandleData);

      const parsedCandles = rawCandleData
        .map(candle => {
          if (!candle || typeof candle !== 'object') {
            //logger.warn("useOlderCandleHistory", "Invalid candle object:", candle);
            return null;
          }
          // NEW: Divide by 1e8 to normalize prices (match real-time hook; adjust if decimals differ)
          const open = parseFloat(candle.open) / 1e8 || 0;
          const high = parseFloat(candle.high) / 1e8 || 0;
          const low = parseFloat(candle.low) / 1e8 || 0;
          const close = parseFloat(candle.close) / 1e8 || 0;
          const volume = parseFloat(candle.volume) || 0;
          const time = new Date(candle.timestamp).getTime() / 1000; // Convert to Unix timestamp
          if (isNaN(open) || isNaN(high) || isNaN(low) || isNaN(close) || isNaN(volume) || isNaN(time)) {
            //logger.warn("useOlderCandleHistory", "Invalid older candle data:", candle);
            return null;
          }
          // NEW: Skip zeroed/invalid candles to avoid cluttering chart with dummies
          if (volume === 0 || (open === high  && high === low && low === close )) {
            //logger.warn("useOlderCandleHistory", Skipping zeroed candle at ${time});
            return null;
          }
          // NEW: Log sample parsed candle for debugging (remove in production if too verbose)
          console.log("Parsed older candle:", { time, open, high, low, close, volume });
          return { time, open, high, low, close, volume };
        })
        .filter(candle => candle !== null)
        .sort((a, b) => a.time - b.time);

      const parsedVolume = parsedCandles.map(candle => ({
        time: candle.time,
        value: candle.volume,
        // NEW: Dynamic color based on candle direction (green for increase, red for decrease, grey neutral)
        // This mirrors real-time hook's intent; adjust if backend provides event types
        color: candle.close > candle.open ? '#26a69a' : candle.close < candle.open ? '#ef5350' : '#78909c'
      }));

      setCandleData(parsedCandles);
      setVolumeData(parsedVolume);
      setError(null);
      //logger.info("useOlderCandleHistory", "Final older candle data:", parsedCandles);
    } catch (err) {
      //logger.error("useOlderCandleHistory", "Error fetching older candles:", err.response?.data?.message || err.message);
      //setError("Error fetching older candle data: " + (err.response?.data?.message || err.message));
      setCandleData([]);
      setVolumeData([]);
    } finally {
      setLoading(false);
    }
  }, [artistId, timeframe]);

  useEffect(() => {
    if (artistId) {
      fetchCandleData();
    } else {
      //logger.error("useOlderCandleHistory", "Invalid artist ID");
      setError("Invalid artist ID");
      setLoading(false);
    }
  }, [fetchCandleData, artistId]);

  return { candleData, volumeData, error, loading, refresh: fetchCandleData };
};