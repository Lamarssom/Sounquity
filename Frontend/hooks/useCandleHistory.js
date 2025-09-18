import { useEffect, useState, useCallback } from "react";
import { getHttpWeb3 } from "../utilities/web3";
import rawAbi from "../abis/ArtistSharesTokenABI.json";
import logger from "../utilities/logger"; // Import logger

const ArtistSharesTokenABI = rawAbi.abi || rawAbi;
const TIMEFRAME_MAP = {
  "1m": 60,
  "5m": 300,
  "15m": 900,
  "1h": 3600,
  "4h": 14400,
  "1D": 86400,
};

export const useCandleHistory = (contractAddress, timeframe = "5m") => {
  const [web3, setWeb3] = useState(null);
  const [contract, setContract] = useState(null);
  const [error, setError] = useState(null);
  const [candleData, setCandleData] = useState([]);
  const [volumeData, setVolumeData] = useState([]);
  const [totalSupply, setTotalSupply] = useState(1000000000);
  const [isInitialized, setIsInitialized] = useState(false);

  const initWeb3 = useCallback(async () => {
    if (!contractAddress) {
      logger.error("useCandleHistory", "Invalid contract address");
      setError("Invalid contract address");
      setIsInitialized(true);
      return;
    }
    try {
      const instance = await getHttpWeb3();
      setWeb3(instance);
      const contractInstance = new instance.eth.Contract(ArtistSharesTokenABI, contractAddress);
      setContract(contractInstance);
      let totalSupplyValue = null;
      try {
        const supply = await contractInstance.methods.totalSupply().call();
        totalSupplyValue = Number(instance.utils.fromWei(supply, "ether"));
        if (isNaN(totalSupplyValue) || totalSupplyValue <= 0) {
          throw new Error("Invalid totalSupply from contract");
        }
        logger.info("useCandleHistory", "Contract initialized at:", contractInstance._address, "Total Supply:", totalSupplyValue);
      } catch (err) {
        logger.warn("useCandleHistory", "Failed to fetch totalSupply, using default:", err.message);
        totalSupplyValue = 1000000000;
      }
      setTotalSupply(totalSupplyValue);
      setIsInitialized(true);
    } catch (err) {
      logger.error("useCandleHistory", "Web3/Contract initialization failed:", err.message);
      setError("Web3/Contract initialization failed: " + err.message);
      setIsInitialized(true);
    }
  }, [contractAddress]);

  const fetchCandles = useCallback(async (retryCount = 0, maxRetries = 3) => {
    if (!contract || !web3 || !contractAddress) {
      logger.warn("useCandleHistory", "Missing contract, web3, or address");
      setCandleData([]);
      setVolumeData([]);
      setError("Missing required dependencies for fetching candles");
      return;
    }

    const interval = TIMEFRAME_MAP[timeframe] || 300;
    try {
      const { timestamps, opens, highs, lows, closes, volumes } = await contract.methods.getCandleHistory(interval).call();
      const buyEvents = await contract.getPastEvents("SharesBought", {
        fromBlock: 0,
        toBlock: "latest",
      });
      const sellEvents = await contract.getPastEvents("SharesSold", {
        fromBlock: 0,
        toBlock: "latest",
      });

      if (buyEvents.length + sellEvents.length < 1 && retryCount < maxRetries) {
        logger.warn("useCandleHistory", "Retrying fetch due to no events", { retryCount, maxRetries });
        setTimeout(() => fetchCandles(retryCount + 1, maxRetries), 2000);
        return;
      }

      const eventMap = {};
      buyEvents.forEach(event => {
        const timestamp = Number(event.returnValues.timestamp);
        const candleTime = Math.floor(timestamp / interval) * interval;
        eventMap[candleTime] = 'buy';
      });
      sellEvents.forEach(event => {
        const timestamp = Number(event.returnValues.timestamp);
        const candleTime = Math.floor(timestamp / interval) * interval;
        eventMap[candleTime] = 'sell';
      });

      if (timestamps.length === 0) {
        logger.warn("useCandleHistory", "No timestamps returned from contract");
      }

      const parsedCandles = timestamps
        .map((time, i) => {
          const rawOpen = opens[i];
          const rawHigh = highs[i];
          const rawLow = lows[i];
          const rawClose = closes[i];
          const volume = Number(volumes[i] || 0);
          const open = rawOpen ? parseFloat((Number(rawOpen) / 1e8).toFixed(8)) : 0;
          const high = rawHigh ? parseFloat((Number(rawHigh) / 1e8).toFixed(8)) : 0;
          const low = rawLow ? parseFloat((Number(rawLow) / 1e8).toFixed(8)) : 0;
          const close = rawClose ? parseFloat((Number(rawClose) / 1e8).toFixed(8)) : 0;
          if (isNaN(open) || isNaN(high) || isNaN(low) || isNaN(close)) {
            logger.warn("useCandleHistory", `Invalid candle data at ${time}: { open, high, low, close, volume }`);
            return null;
          }
          return {
            time: Number(time) * 1000,
            open,
            high,
            low,
            close,
            volume,
          };
        })
        // Filter out zeroed/invalid candles after mapping
        .filter(candle => candle && !(candle.open === 0 && candle.high === 0 && candle.low === 0 && candle.close === 0 && candle.volume === 0))
        .sort((a, b) => a.time - b.time);

      const volumeData = timestamps
        .map((time, i) => {
          const volume = Number(volumes[i] || 0);
          if (isNaN(volume)) {
            logger.warn("useCandleHistory", `Invalid volume data at ${time}`);
            return null;
          }
          const eventType = eventMap[Number(time)];
          const color = eventType === 'buy' ? '#26a69a' : eventType === 'sell' ? '#ef5350' : '#78909c';
          return {
            time: Number(time) * 1000,
            value: volume,
            color,
          };
        })
        .filter(volume => volume !== null)
        .sort((a, b) => a.time - b.time);
      
      setCandleData(prevCandles => {
        const prevTimes = new Set(prevCandles.map(c => c.time));
        const newCandles = parsedCandles.filter(c => !prevTimes.has(c.time));
        if (newCandles.length === 0 && prevCandles.length === parsedCandles.length) {
          logger.debug("useCandleHistory", "No new candles to update");
          return prevCandles;
        }
        const updatedCandles = [...prevCandles, ...newCandles].sort((a, b) => a.time - b.time);
        logger.info("useCandleHistory", "Updated candle data:", updatedCandles.length, "candles");
        return updatedCandles;
      });
      setVolumeData(prevVolume => {
        const prevTimes = new Set(prevVolume.map(v => v.time));
        const newVolume = volumeData.filter(v => !prevTimes.has(v.time));
        if (newVolume.length === 0 && prevVolume.length === volumeData.length) {
          return prevVolume;
        }
        const updatedVolume = [...prevVolume, ...newVolume].sort((a, b) => a.time - b.time);
        logger.info("useCandleHistory", "Updated volume data:", updatedVolume.length, "bars");
        return updatedVolume;
      });

      setError(null);
    } catch (err) {
      logger.error("useCandleHistory", "Error fetching candles:", err.message);
      setCandleData([]);
      setVolumeData([]);
      setError("Error fetching candle data: " + err.message);
    }
  }, [contract, contractAddress, web3, timeframe]);

  useEffect(() => {
    initWeb3();
  }, [initWeb3]);

  useEffect(() => {
    if (contract && web3 && isInitialized && contractAddress) {
      fetchCandles();
      const intervalId = setInterval(fetchCandles, 30000);
      return () => clearInterval(intervalId);
    }
  }, [contract, web3, fetchCandles, isInitialized, contractAddress]);

  return {
    web3,
    contract,
    candleData,
    volumeData,
    error,
    refreshHistory: fetchCandles,
    totalSupply,
  };
};