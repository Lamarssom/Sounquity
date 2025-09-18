import React, { useRef, useState, useEffect, useImperativeHandle, forwardRef, useCallback } from 'react';
import { useCandleHistory } from '../hooks/useCandleHistory';
import { useOlderCandleHistory } from '../hooks/useOlderCandleHistory';
import { createArtistChart } from '../utilities/createArtistChart';
import { Spinner } from 'react-bootstrap';

const isDev = process.env.NODE_ENV === 'development';
const criticalLogs = ['Applied', 'Initial time scale options', 'Volume data', 'Updated', 'Skipped', 'Formatting candle'];
const log = (...args) => {
  if (isDev && criticalLogs.some(keyword => args[0]?.includes(keyword))) {
    console.log(...args);
  }
};

const TIMEFRAMES = ['1m', '5m', '15m', '1h', '4h', '1D'];
const REALTIME_CUTOFF_HOURS = 2; // Real-time candles for the last 2 hours

const ArtistChart = forwardRef(({ contractAddress, artistId, timeframe = '5m', refreshTrigger }, ref) => {
  const [selectedTimeframe, setSelectedTimeframe] = useState(timeframe);
  const [watchlist, setWatchlist] = useState(JSON.parse(localStorage.getItem('watchlist') || '[]'));
  const [alertPrice, setAlertPrice] = useState('');
  const [displayMode, setDisplayMode] = useState('price');
  const chartContainerRef = useRef(null);
  const chartRef = useRef(null);
  const candleSeriesRef = useRef(null);
  const volumeSeriesRef = useRef(null);
  const cleanupRef = useRef(null);
  const chartInstanceRef = useRef(null);
  const hasInitializedRef = useRef(false);
  const isMountedRef = useRef(true);
  const [chartReady, setChartReady] = useState(false);
  const [userInteracted, setUserInteracted] = useState(false);
  const appliedCandlesRef = useRef(new Set());
  const appliedVolumeRef = useRef(new Set());
  const lastCandleTimeRef = useRef(null);
  const lastVolumeTimeRef = useRef(null);

  const { candleData: realTimeCandles, volumeData: realTimeVolume, loading: realTimeLoading, error: realTimeError, refreshHistory, totalSupply } = useCandleHistory(contractAddress, selectedTimeframe);
  const { candleData: olderCandles, volumeData: olderVolume, loading: olderLoading, error: olderError, refresh: refreshOlderHistory } = useOlderCandleHistory(artistId, selectedTimeframe);

  // Merge candles and volume, using a cutoff to avoid duplicates
  const cutoffTime = Math.floor(Date.now() / 1000) - (REALTIME_CUTOFF_HOURS * 3600); // Last 2 hours
  const mergedCandles = [
    ...olderCandles.filter(candle => candle.time < cutoffTime),
    ...realTimeCandles.filter(candle => candle.time >= cutoffTime)
  ].sort((a, b) => a.time - b.time);
  const mergedVolume = [
    ...olderVolume.filter(volume => volume.time < cutoffTime),
    ...realTimeVolume.filter(volume => volume.time >= cutoffTime)
  ].sort((a, b) => a.time - b.time);

  const safeRefresh = useCallback(() => {
    if (chartReady) {
      log('[ArtistChart] 🔄 Triggering data refresh');
      refreshHistory();
      refreshOlderHistory();
    }
  }, [refreshHistory, refreshOlderHistory, chartReady]);

  const addToWatchlist = () => {
    if (!watchlist.includes(contractAddress)) {
      const newWatchlist = [...watchlist, contractAddress];
      setWatchlist(newWatchlist);
      localStorage.setItem('watchlist', JSON.stringify(newWatchlist));
      log('[ArtistChart] 🟢 Added to watchlist:', contractAddress);
    }
  };

  const setPriceAlert = () => {
    if (!alertPrice || isNaN(alertPrice)) return;
    log('[ArtistChart] 🔔 Price alert set for:', alertPrice);
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      if (mergedCandles.length > 0 && mergedCandles[mergedCandles.length - 1].close >= parseFloat(alertPrice)) {
        new Notification(`Price alert: ${contractAddress} reached ${alertPrice}`);
      }
    } else if (typeof Notification !== 'undefined' && Notification.permission !== 'denied') {
      Notification.requestPermission().then(permission => {
        if (permission === 'granted') setPriceAlert();
      });
    }
  };

  const formatNumberWithSuffix = (value) => {
    if (isNaN(value) || value <= 0) return '0';
    const absValue = Math.abs(value);
    if (absValue >= 1e9) {
      return `${(value / 1e9).toFixed(2)}B`;
    } else if (absValue >= 1e6) {
      return `${(value / 1e6).toFixed(2)}M`;
    } else if (absValue >= 1e3) {
      return `${(value / 1e3).toFixed(2)}K`;
    }
    return value.toFixed(2);
  };

  useEffect(() => {
    log('[ArtistChart] ⬆ Mounting component');
    isMountedRef.current = true;
    return () => {
      log('[ArtistChart] ⬇ Unmounting component');
      isMountedRef.current = false;
      if (cleanupRef.current) {
        cleanupRef.current();
      }
    };
  }, []);

  useEffect(() => {
    let retryCount = 0;
    let rafId;

    const waitForContainer = () => {
      const container = chartContainerRef.current;
      if (!container) {
        if (retryCount < 20) {
          retryCount++;
          log(`[ArtistChart] ⏳ Waiting for container, retry ${retryCount}/20`);
          rafId = requestAnimationFrame(waitForContainer);
        } else {
          console.error('[ArtistChart] ❌ chartContainerRef still null after max retries — aborting init');
        }
        return;
      }

      if (hasInitializedRef.current) {
        log('[ArtistChart] 🟢 Chart already initialized, skipping');
        return;
      }

      log('[ArtistChart] ✅ Container found — initializing chart for contract:', contractAddress, 'timeframe:', selectedTimeframe);
      const chartInstance = createArtistChart(chartContainerRef.current, displayMode, formatNumberWithSuffix);
      if (!chartInstance.chart || !chartInstance.candleSeries || !chartInstance.volumeSeries) {
        console.error('[ArtistChart] ❌ Chart initialization incomplete:', chartInstance);
        return;
      }

      chartRef.current = chartInstance.chart;
      candleSeriesRef.current = chartInstance.candleSeries;
      volumeSeriesRef.current = chartInstance.volumeSeries;
      cleanupRef.current = chartInstance.cleanup;
      chartInstanceRef.current = chartInstance;
      hasInitializedRef.current = true;
      setChartReady(true);
      log('[ArtistChart] 🟢 Initial time scale options:', chartRef.current.timeScale().options());

      const timeScale = chartRef.current.timeScale();
      timeScale.subscribeVisibleTimeRangeChange(() => {
        setUserInteracted(true);
        const currentRange = timeScale.getVisibleRange();
        const currentBarSpacing = timeScale.options().barSpacing;
        log('[ArtistChart] 🟢 User interacted: Time range changed to:', { range: currentRange, barSpacing: currentBarSpacing });
      });
      chartRef.current.subscribeCrosshairMove(() => {
        setUserInteracted(true);
      });

      return () =>  cancelAnimationFrame(rafId);
    };

    const initWhenReady = () => {
      if (chartContainerRef.current) {
        waitForContainer();
      } else rafId = requestAnimationFrame(initWhenReady);
    };

    rafId = requestAnimationFrame(initWhenReady);
    return () => cancelAnimationFrame(rafId);
  }, [contractAddress, selectedTimeframe, displayMode]);

  const fetchMarketCapData = useCallback(async () => {
    if (displayMode !== 'marketCap') return mergedCandles;
    try {
      if (!totalSupply || isNaN(totalSupply) || totalSupply <= 0) {
        console.warn('[ArtistChart] ⚠ Invalid totalSupply, falling back to price mode:', totalSupply);
        return mergedCandles;
      }
      const marketCapData = mergedCandles
        .filter(candle => candle && !isNaN(candle.open) && !isNaN(candle.high) && !isNaN(candle.low) && !isNaN(candle.close))
        .map(candle => ({
          ...candle,
          open: parseFloat(candle.open) * totalSupply,
          high: parseFloat(candle.high) * totalSupply,
          low: parseFloat(candle.low) * totalSupply,
          close: parseFloat(candle.close) * totalSupply,
        }))
        .filter(candle => candle !== null)
        .sort((a, b) => a.time - b.time);
      return marketCapData;
    } catch (err) {
      console.error('[ArtistChart] ❌ Error processing market cap:', err);
      return mergedCandles;
    }
  }, [mergedCandles, displayMode, totalSupply]);

  useEffect(() => {
    if (!chartReady || !Array.isArray(mergedCandles) || !candleSeriesRef.current || !volumeSeriesRef.current) {
      return;
    }

    const applyChartData = async () => {
      try {
        const timeScale = chartRef.current.timeScale();
        let zoomState = userInteracted ? {
          range: timeScale.getVisibleRange(),
          barSpacing: timeScale.options().barSpacing,
        } : null;

        const dataToApply = await fetchMarketCapData();
        log('[ArtistChart] 🟢 fetchMarketCapData result:', dataToApply);
        const formattedCandleData = dataToApply
          .map(candle => ({
            time: candle.time / 1000,
            open: parseFloat(candle.open) || 0,
            high: parseFloat(candle.high) || 0,
            low: parseFloat(candle.low) || 0,
            close: parseFloat(candle.close) || 0,
          }))
          .filter(candle => {
            if (candle.open === 0 && candle.high === 0 && candle.low === 0 && candle.close === 0) {
              console.warn('[ArtistChart] ⚠ Skipping candle with all zero OHLC at', candle.time);
            }
            return !isNaN(candle.open) && !isNaN(candle.high) && !isNaN(candle.low) && !isNaN(candle.close);
          })
          .sort((a, b) => a.time - b.time);

        const volumeFormatted = mergedVolume
          .map(v => {
            log("[ArtistChart] 📊 Volume formatted:", v);
            return {
              time: v.time / 1000,
              value: parseFloat(v.value) || 0,
              color: v.color,
            };
          })
          .filter(v => !isNaN(v.value) && v.value >= 0)
          .sort((a, b) => a.time - b.time);

        candleSeriesRef.current.setData(formattedCandleData);
        appliedCandlesRef.current.clear();
        formattedCandleData.forEach(c => appliedCandlesRef.current.add(c.time));
        lastCandleTimeRef.current = formattedCandleData.length > 0 ? Math.max(...formattedCandleData.map(c => c.time)) : null;
        //log('[ArtistChart] 🟢 Applied', formattedCandleData.length, 'candles');

        volumeSeriesRef.current.setData(volumeFormatted);
        appliedVolumeRef.current.clear();
        volumeFormatted.forEach(v => appliedVolumeRef.current.add(v.time));
        lastVolumeTimeRef.current = volumeFormatted.length > 0 ? Math.max(...volumeFormatted.map(v => v.time)) : null;
        //log('[ArtistChart] 🟢 Applied', volumeFormatted.length, 'volume bars');

        // Throttle formatter logs
        let lastLogTime = 0;
        const LOG_THROTTLE_MS = 10000;

        // Reset price scale to ensure new settings apply
        chartRef.current.priceScale('right').applyOptions({ priceFormat: {} });
        chartRef.current.priceScale('right').applyOptions({
          autoScale: true,
          scaleMargins: { top: 0.3, bottom: 0.3 },
          minimumPrice: displayMode === 'marketCap' 
            ? Math.min(...formattedCandleData.map(c => c.low)) * 0.95 
            : Math.max(0, Math.min(...formattedCandleData.map(c => c.low)) - 0.05),
          maximumPrice: displayMode === 'marketCap' 
            ? Math.max(...formattedCandleData.map(c => c.high)) * 1.05 
            : Math.max(...formattedCandleData.map(c => c.high)) + 0.05,
          priceFormat: {
            type: 'custom',
            formatter: (price) => {
              if (displayMode === 'marketCap') {
                const formatted = formatNumberWithSuffix(price);
                if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
                  log('[ArtistChart] 📊 Formatting market cap price:', { input: price, output: formatted });
                  lastLogTime = Date.now();
                }
                return formatted;
              }
              const formatted = price.toFixed(2);
              if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
                log('[ArtistChart] 📊 Formatting price:', { input: price, output: formatted });
                lastLogTime = Date.now();
              }
              return formatted;
            },
            precision: 2,
            minMove: displayMode === 'marketCap' ? 0.01 : 0.01,
          },
        });
        log('[ArtistChart] 🟢 Price scale options applied:', chartRef.current.priceScale('right').options());

        candleSeriesRef.current.applyOptions({
          priceFormat: {
            type: 'custom',
            formatter: (price) => {
              if (displayMode === 'marketCap') {
                const formatted = formatNumberWithSuffix(price);
                if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
                  log('[ArtistChart] 📊 Formatting market cap price (series):', { input: price, output: formatted });
                  lastLogTime = Date.now();
                }
                return formatted;
              }
              const formatted = price.toFixed(2);
              if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
                log('[ArtistChart] 📊 Formatting price (series):', { input: price, output: formatted });
                lastLogTime = Date.now();
              }
              return formatted;
            },
            precision: 2,
            minMove: displayMode === 'marketCap' ? 0.01 : 0.01,
          },
        });
        log('[ArtistChart] 🟢 Candlestick series options updated:', candleSeriesRef.current.options());

        chartRef.current.priceScale('volume').applyOptions({ autoScale: true });

        // Only set initial range on first render or timeframe change
        if (!userInteracted && !hasInitializedRef.current && formattedCandleData.length > 0) {
          const lastCandles = Math.min(50, formattedCandleData.length);
          timeScale.setVisibleRange({
            from: formattedCandleData[formattedCandleData.length - lastCandles].time,
            to: formattedCandleData[formattedCandleData.length - 1].time + 300,
          });
        } else if (userInteracted && zoomState?.range?.from && zoomState?.range?.to) {
          const minTime = Math.min(...formattedCandleData.map(c => c.time));
          const maxTime = Math.max(...formattedCandleData.map(c => c.time));
          const buffer = (maxTime - minTime) * 0.1; // Smaller buffer for smoother updates
          if (zoomState.range.from <= maxTime + buffer && zoomState.range.to >= minTime - buffer) {
            timeScale.setVisibleRange(zoomState.range);
            timeScale.applyOptions({ barSpacing: zoomState.barSpacing });
          } else {
            const lastCandles = Math.min(50, formattedCandleData.length);
            timeScale.setVisibleRange({
              from: formattedCandleData[formattedCandleData.length - lastCandles].time,
              to: formattedCandleData[formattedCandleData.length - 1].time + 300,
            });
          }
        }
      } catch (err) {
        console.error('[ArtistChart] ❌ Failed to apply chart data:', err);
      }
    };

    const debounceTimeout = setTimeout(() => {
      if (isMountedRef.current) applyChartData();
    }, 1000);

    return () => clearTimeout(debounceTimeout);
  }, [chartReady, mergedCandles, mergedVolume, displayMode, fetchMarketCapData, selectedTimeframe, userInteracted, totalSupply]);

  useImperativeHandle(ref, () => ({
    updateCandles: () => {
      if (!chartReady) log('[ArtistChart] ⚠ Chart not ready for update');
      else log('[ArtistChart] 🔄 Forcing candle and volume update');
      refreshHistory();
      refreshOlderHistory();
      setTimeout(() => {
        refreshHistory();
        refreshOlderHistory();
      }, 3000);
    },
  }));

  useEffect(() => {
    log('[ArtistChart] 🕒 Timeframe changed, clearing data:', selectedTimeframe);
    if (candleSeriesRef.current) candleSeriesRef.current.setData([]);
    if (volumeSeriesRef.current) volumeSeriesRef.current.setData([]);
    appliedCandlesRef.current.clear();
    appliedVolumeRef.current.clear();
    lastCandleTimeRef.current = null;
    lastVolumeTimeRef.current = null;
    setUserInteracted(false);
    refreshHistory();
    refreshOlderHistory();
  }, [selectedTimeframe, refreshHistory, refreshOlderHistory]);

  useEffect(() => {
    if (refreshTrigger && chartReady) {
      log('[ArtistChart] 🔄 Refresh triggered by refreshTrigger');
      safeRefresh();
    }
  }, [refreshTrigger, safeRefresh, chartReady]);

  return (
    <div className="chart-wrapper">
      <div className="chart-controls" style={{ display: 'flex', justifyContent: 'center', gap: '10px', flexWrap: 'wrap' }}>
        {TIMEFRAMES.map(tf => (
          <button
            key={tf}
            onClick={() => {
              log('[ArtistChart] 🕒 Changing timeframe to:', tf);
              setSelectedTimeframe(tf);
              setUserInteracted(false);
            }}
            className={selectedTimeframe === tf ? 'active' : ''}
          >
            {tf}
          </button>
        ))}
        <button
          onClick={() => setDisplayMode(displayMode === 'price' ? 'marketCap' : 'price')}
          className={displayMode === 'marketCap' ? 'active' : ''}
        >
          {displayMode === 'price' ? 'Show Market Cap' : 'Show Price'}
        </button>
        <button onClick={addToWatchlist} className="chart-control-btn">
          Watch
        </button>
        <input
          type="number"
          placeholder="Alert Price"
          value={alertPrice}
          onChange={e => setAlertPrice(e.target.value)}
          className="chart-control-input"
        />
        <button onClick={setPriceAlert} className="chart-control-btn">
          Alert
        </button>
      </div>
      <div
        ref={chartContainerRef}
        className="chart-container"
      />
      {(realTimeLoading || olderLoading) && (
        <div className="chart-overlay">
          <Spinner animation="border" />
        </div>
      )}
      {(realTimeError || olderError) && (
        <div className="chart-overlay text-danger">
          {realTimeError || olderError}
          <br />
          <button onClick={() => window.location.reload()} className="retry-btn">
            Retry
          </button>
        </div>
      )}
      {!chartReady && (
        <div className="chart-overlay text-muted">
          <Spinner animation="border" /> Initializing chart...
        </div>
      )}
      {mergedCandles.length === 0 && !realTimeLoading && !olderLoading && !realTimeError && !olderError && (
        <div className="chart-overlay text-muted">
          No historical price data available yet.
        </div>
      )}
      <div className="chart-footer">
        Contract: {contractAddress} | Data points: {mergedCandles.length} | Powered by{' '}
        <a href="https://www.tradingview.com/" target="_blank" rel="noopener noreferrer">
          TradingView
        </a>
      </div>
    </div>
  );
});

export default ArtistChart;