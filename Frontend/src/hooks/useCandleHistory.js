import { useEffect, useState, useCallback } from "react";
import { getHttpWeb3 } from "../utilities/web3";
import rawAbi from "../abis/ArtistSharesTokenABI.json";
import logger from "../utilities/logger";

const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

export const useCandleHistory = (contractAddress, artistId, timeframe = "5m") => {
  const [candleData, setCandleData] = useState([]);
  const [volumeData, setVolumeData] = useState([]);
  const [totalSupply, setTotalSupply] = useState(1000000000);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const initWeb3 = async () => {
      try {
        const instance = await getHttpWeb3();
        const contractInstance = new instance.eth.Contract(ArtistSharesTokenABI, contractAddress);
        const supply = await contractInstance.methods.totalSupply().call();
        const totalSupplyNumber = Number(instance.utils.fromWei(supply, "ether"));
        setTotalSupply(totalSupplyNumber);
        logger.info("useCandleHistory", "Fetched totalSupply:", totalSupplyNumber);
      } catch (err) {
        logger.warn("useCandleHistory", "Failed to fetch totalSupply, using default:", err.message);
        setTotalSupply(1000000000);
      }
    };
    initWeb3();
  }, [contractAddress]);

  const fetchCandles = useCallback(async () => {
    setLoading(true);
    try {
      const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
      const response = await fetch(`${apiUrl}/artists/candleData?artistId=${artistId}&timeframe=${timeframe.toUpperCase()}`);
      if (!response.ok) throw new Error(`Failed to fetch candles: ${response.statusText}`);
      const data = await response.json();
      logger.info("Raw API candle:", {
        open: data[0]?.open,
        high: data[0]?.high, 
        low: data[0]?.low,
        close: data[0]?.close,
        spread: Number(data[0]?.high) - Number(data[0]?.low),
        rawTypes: {
          openType: typeof data[0]?.open,
          highType: typeof data[0]?.high,
          lowType: typeof data[0]?.low,
          closeType: typeof data[0]?.close
        }
      });
      logger.info("useCandleHistory", "Fetched candle data:", data);

      // DEBUG: Log raw API response
      if (data && data.length > 0) {
        const firstCandle = data[0];
        logger.info("Raw API candle:", {
          open: firstCandle.open,
          high: firstCandle.high,
          low: firstCandle.low,
          close: firstCandle.close,
          volume: firstCandle.volume,
          timestamp: firstCandle.timestamp,
          spread: Number(firstCandle.high) - Number(firstCandle.low)
        });
      }

      const parsedCandles = data
        .map(c => {
          // <<< REPLACE FROM HERE >>>
          const open  = parseFloat(c.open);
          const high  = parseFloat(c.high);
          const low   = parseFloat(c.low);
          const close = parseFloat(c.close);

          const time = new Date(c.timestamp).getTime();

          console.log("Parsed candle values:", {
            time,
            open: open.toFixed(10),
            high: high.toFixed(10),
            low: low.toFixed(10),
            close: close.toFixed(10),
            volume: parseFloat(c.volume),
            identical: open === high && high === low && low === close,
            spread: high - low
          });

          return {
            time,
            open,
            high,
            low,
            close,
            volume: parseFloat(c.volume) || 0,
          };
        })
        .filter(c => !isNaN(c.time) && !isNaN(c.open) && !isNaN(c.high) && !isNaN(c.low) && !isNaN(c.close) && !isNaN(c.volume))
        .sort((a, b) => a.time - b.time);

      const volumeFormatted = data
        .map(c => ({
          time: new Date(c.timestamp).getTime(),
          value: parseFloat(c.volume) || 0,  // ← c.volume is number, just parseFloat
          color: c.lastEventType === 'BUY' ? '#26a69a' : c.lastEventType === 'SELL' ? '#ef5350' : '#78909c',
        }))
        .filter(v => !isNaN(v.value) && v.value >= 0)
        .sort((a, b) => a.time - b.time);

      console.log("Final candleData count:", parsedCandles.length);
      setCandleData(parsedCandles);
      setVolumeData(volumeFormatted);
      setError(null);
    } catch (err) {
      logger.error("useCandleHistory", "Error fetching candles:", err.message);
      setError("Error fetching candle data: " + err.message);
      setCandleData([]);
      setVolumeData([]);
    } finally {
      setLoading(false);
    }
  }, [artistId, timeframe]);

  useEffect(() => {
    if (artistId) {
      fetchCandles();
      const intervalId = setInterval(fetchCandles, 30000);
      return () => clearInterval(intervalId);
    }
  }, [fetchCandles, artistId]);

  return {
    candleData,
    volumeData,
    loading,
    error,
    totalSupply,
  };
};