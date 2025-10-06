import { useEffect, useState, useCallback } from "react";
import { getHttpWeb3 } from "../utilities/web3";
import rawAbi from "../abis/ArtistSharesTokenABI.json";
import logger from "../utilities/logger";

const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

export const useCandleHistory = (contractAddress, artistId, timeframe = "5m") => {
  const [candleData, setCandleData] = useState([]);
  const [volumeData, setVolumeData] = useState([]);
  const [totalSupply, setTotalSupply] = useState(1000000);
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
        setTotalSupply(1000000);
      }
    };
    initWeb3();
  }, [contractAddress]);

  const fetchCandles = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/api/artists/candleData?artistId=${artistId}&timeframe=${timeframe.toUpperCase()}`);
      if (!response.ok) throw new Error(`Failed to fetch candles: ${response.statusText}`);
      const data = await response.json();
      logger.info("useCandleHistory", "Fetched candle data:", data);

      const parsedCandles = data
        .map(c => ({
          time: new Date(c.timestamp).getTime(),
          open: parseFloat(Number(c.open).toFixed(8)) || 0,
          high: parseFloat(Number(c.high).toFixed(8)) || 0,
          low: parseFloat(Number(c.low).toFixed(8)) || 0,
          close: parseFloat(Number(c.close).toFixed(8)) || 0,
          volume: parseFloat(Number(c.volume).toFixed(8)) || 0,
        }))
        .filter(c => !isNaN(c.time) && !isNaN(c.open) && !isNaN(c.high) && !isNaN(c.low) && !isNaN(c.close) && !isNaN(c.volume))
        .sort((a, b) => a.time - b.time);

      const volumeFormatted = data
        .map(c => ({
          time: new Date(c.timestamp).getTime(),
          value: parseFloat(Number(c.volume).toFixed(8)) || 0,
          color: c.lastEventType === 'BUY' ? '#26a69a' : c.lastEventType === 'SELL' ? '#ef5350' : '#78909c',
        }))
        .filter(v => !isNaN(v.time) && !isNaN(v.value) && v.value >= 0)
        .sort((a, b) => a.time - b.time);

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
