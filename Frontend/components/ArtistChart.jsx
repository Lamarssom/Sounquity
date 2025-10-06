import React, { useRef, useState, useEffect, useImperativeHandle, forwardRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { useCandleHistory } from '../hooks/useCandleHistory';
import { createArtistChart } from '../utilities/createArtistChart';
import { Spinner } from 'react-bootstrap';

const isDev = process.env.NODE_ENV === 'development';
const criticalLogs = ['Applied', 'Initial time scale options', 'Volume data', 'Updated', 'Skipped', 'Formatting candle'];
const log = (...args) => {
  if (isDev && criticalLogs.some(keyword => args[0]?.includes(keyword))) {
    console.log(...args);
  }
};

const TIMEFRAMES = ['1m', '5m', '15m', '30m', '1h', '4h', '1D', '1W'];
const TIMEFRAME_MAP = {
  '1m': 60,
  '5m': 300,
  '15m': 900,
  '30m': 1800,
  '1h': 3600,
  '4h': 14400,
  '1D': 86400,
  '1W': 604800,
};
const REALTIME_CUTOFF_HOURS = 2;

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
  const stompClient = useRef(null);
  const [candles, setCandles] = useState([]);
  const [volume, setVolume] = useState([]);
  const { candleData: initialCandles, volumeData: initialVolume, loading: initialLoading, error: initialError, totalSupply } = useCandleHistory(contractAddress, artistId, selectedTimeframe);

  const safeRefresh = useCallback(() => {
    if (chartReady) {
      log('[ArtistChart] 🔄 Triggering data refresh');
    }
  }, [chartReady]);

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
      if (candles.length > 0 && candles[candles.length - 1].close >= parseFloat(alertPrice)) {
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
    if (absValue >= 1e9) return `$${(value / 1e9).toFixed(2)}B`;
    else if (absValue >= 1e6) return `$${(value / 1e6).toFixed(2)}M`;
    else if (absValue >= 1e3) return `$${(value / 1e3).toFixed(2)}K`;
    return `$${value.toFixed(2)}`;
  };

  useEffect(() => {
    log('[ArtistChart] ⬆ Mounting component');
    isMountedRef.current = true;

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws', null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
        timeout: 15000,
      }),
      reconnectDelay: 10000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        log('[ArtistChart] STOMP Debug: ', str);
      },
    });

    client.onConnect = (frame) => {
      log('[ArtistChart] Successfully connected to WebSocket: ', JSON.stringify(frame));
      client.subscribe(`/topic/trades/${artistId}`, (message) => {
        log('[ArtistChart] Subscribed to /topic/trades/', artistId);
        log('[ArtistChart] Received trade update:', message.body);
        try {
          const trade = JSON.parse(message.body);
          log('[ArtistChart] Parsed trade:', trade);
          updateChartWithTrade(trade);
          if (message.headers['message-id']) {
            client.ack(message.headers['message-id'], message.headers.subscription);
          }
        } catch (err) {
          console.error('[ArtistChart] Error processing trade message:', err);
        }
      }, { 'ack': 'client' });

      client.subscribe(`/topic/financials/${artistId}`, (message) => {
        log('[ArtistChart] Subscribed to /topic/financials/', artistId);
        log('[ArtistChart] Received financials update:', message.body);
        if (message.headers['message-id']) {
          client.ack(message.headers['message-id'], message.headers.subscription);
        }
      }, { 'ack': 'client' });
    };

    client.onStompError = (frame) => {
      console.error('[ArtistChart] STOMP Error:', frame.headers?.message || 'Unknown error', frame);
    };

    client.onWebSocketError = (error) => {
      console.error('[ArtistChart] WebSocket Error:', error.message || error);
    };

    client.onWebSocketClose = (event) => {
      console.warn('[ArtistChart] WebSocket Closed: ', event.reason || 'No reason provided');
    };

    client.beforeConnect = () => {
      log('[ArtistChart] Attempting to connect to WebSocket for artistId: ', artistId);
    };

    client.activate();
    stompClient.current = client;

    return () => {
      log('[ArtistChart] ⬇ Unmounting component');
      isMountedRef.current = false;
      if (cleanupRef.current) cleanupRef.current();
      if (stompClient.current) {
        stompClient.current.deactivate();
        log('[ArtistChart] WebSocket client deactivated');
      }
    };
  }, [artistId]);

  const updateChartWithTrade = (trade) => {
    if (!chartReady || !isMountedRef.current) return;

    const tradeDate = trade.timestamp && Array.isArray(trade.timestamp)
      ? new Date(Date.UTC(trade.timestamp[0], trade.timestamp[1] - 1, trade.timestamp[2], trade.timestamp[3], trade.timestamp[4], trade.timestamp[5]))
      : new Date();
    const tradeTime = tradeDate.getTime();

    const interval = TIMEFRAME_MAP[selectedTimeframe] * 1000; // ms
    const candleTime = Math.floor(tradeTime / interval) * interval;

    const price = parseFloat(trade.priceInUsd.toFixed(8)) || 0.01; // Round to 8 decimals
    const amount = Number(trade.amount) || 0;
    const color = trade.eventType === 'BUY' ? '#26A69A' : '#EF5350'; // Green for BUY, red for SELL

    log('[ArtistChart] Processing trade:', { tradeTime, price, amount, eventType: trade.eventType, candleTime });

    setCandles(prev => {
      const updated = [...prev];
      const index = updated.findIndex(c => c.time === candleTime);
      let newCandle;
      if (index === -1) {
        newCandle = { time: candleTime, open: price, high: price, low: price, close: price };
        updated.push(newCandle);
      } else {
        newCandle = { ...updated[index] };
        newCandle.high = Math.max(newCandle.high, price);
        newCandle.low = Math.min(newCandle.low, price);
        newCandle.close = price;
        updated[index] = newCandle;
      }
      log('[ArtistChart] Updated candle:', newCandle);
      return updated.sort((a, b) => a.time - b.time);
    });

    setVolume(prev => {
      const updated = [...prev];
      const index = updated.findIndex(v => v.time === candleTime);
      if (index === -1) {
        updated.push({ time: candleTime, value: amount, color });
      } else {
        updated[index].value += amount;
        updated[index].color = color;
      }
      log('[ArtistChart] Updated volume:', { time: candleTime, value: updated[index]?.value || amount, color });
      return updated.sort((a, b) => a.time - b.time);
    });
  };

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

      return () => cancelAnimationFrame(rafId);
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
    if (displayMode !== 'marketCap') return candles;
    try {
      if (!totalSupply || isNaN(totalSupply) || totalSupply <= 0) {
        console.warn('[ArtistChart] ⚠ Invalid totalSupply, falling back to price mode:', totalSupply);
        return candles;
      }
      return candles.map(candle => ({
        ...candle,
        open: parseFloat((candle.open * totalSupply).toFixed(8)),
        high: parseFloat((candle.high * totalSupply).toFixed(8)),
        low: parseFloat((candle.low * totalSupply).toFixed(8)),
        close: parseFloat((candle.close * totalSupply).toFixed(8)),
      }));
    } catch (err) {
      console.error('[ArtistChart] ❌ Error processing market cap:', err);
      return candles;
    }
  }, [candles, displayMode, totalSupply]);

  useEffect(() => {
    if (!chartReady || (!initialCandles.length && !candles.length) || !candleSeriesRef.current || !volumeSeriesRef.current) {
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
        const filteredData = dataToApply.filter(candle => candle && candle.time !== undefined);
        const formattedCandleData = filteredData
          .map(candle => ({
            time: candle.time / 1000,
            open: parseFloat(candle.open.toFixed(8)),
            high: parseFloat(candle.high.toFixed(8)),
            low: parseFloat(candle.low.toFixed(8)),
            close: parseFloat(candle.close.toFixed(8)),
          }))
          .filter(candle => !isNaN(candle.open) && !isNaN(candle.high) && !isNaN(candle.low) && !isNaN(candle.close))
          .sort((a, b) => a.time - b.time);

        const filteredVolume = volume.filter(v => v && v.time !== undefined);
        const volumeFormatted = filteredVolume
          .map(v => ({
            time: v.time / 1000,
            value: parseFloat(v.value) || 0,
            color: v.color,
          }))
          .filter(v => !isNaN(v.value) && v.value >= 0)
          .sort((a, b) => a.time - b.time);

        candleSeriesRef.current.setData(formattedCandleData);
        appliedCandlesRef.current.clear();
        formattedCandleData.forEach(c => appliedCandlesRef.current.add(c.time));
        lastCandleTimeRef.current = formattedCandleData.length > 0 ? Math.max(...formattedCandleData.map(c => c.time)) : null;

        volumeSeriesRef.current.setData(volumeFormatted);
        appliedVolumeRef.current.clear();
        volumeFormatted.forEach(v => appliedVolumeRef.current.add(v.time));
        lastVolumeTimeRef.current = volumeFormatted.length > 0 ? Math.max(...volumeFormatted.map(v => v.time)) : null;

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
            formatter: (price) => displayMode === 'marketCap' ? formatNumberWithSuffix(price) : price.toFixed(2),
            precision: 2,
            minMove: displayMode === 'marketCap' ? 0.01 : 0.01,
          },
        });

        candleSeriesRef.current.applyOptions({
          priceFormat: {
            type: 'custom',
            formatter: (price) => displayMode === 'marketCap' ? formatNumberWithSuffix(price) : price.toFixed(2),
            precision: 2,
            minMove: displayMode === 'marketCap' ? 0.01 : 0.01,
          },
        });

        chartRef.current.priceScale('volume').applyOptions({ autoScale: true });

        if (!userInteracted && !hasInitializedRef.current && formattedCandleData.length > 0) {
          const lastCandles = Math.min(50, formattedCandleData.length);
          timeScale.setVisibleRange({
            from: formattedCandleData[formattedCandleData.length - lastCandles].time,
            to: formattedCandleData[formattedCandleData.length - 1].time + 300,
          });
        } else if (userInteracted && zoomState?.range?.from && zoomState?.range?.to) {
          const minTime = Math.min(...formattedCandleData.map(c => c.time));
          const maxTime = Math.max(...formattedCandleData.map(c => c.time));
          const buffer = (maxTime - minTime) * 0.1;
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

    applyChartData();
  }, [chartReady, candles, volume, displayMode, fetchMarketCapData, userInteracted, totalSupply]);

  useImperativeHandle(ref, () => ({
    updateCandles: () => {
      if (!chartReady) log('[ArtistChart] ⚠ Chart not ready for update');
      else log('[ArtistChart] 🔄 Forcing candle and volume update');
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
    setCandles([]);
    setVolume([]);
  }, [selectedTimeframe]);

  useEffect(() => {
    if (refreshTrigger && chartReady) {
      log('[ArtistChart] 🔄 Refresh triggered by refreshTrigger');
      safeRefresh();
    }
  }, [refreshTrigger, safeRefresh, chartReady]);

  useEffect(() => {
    if (initialCandles.length > 0) {
      setCandles(initialCandles);
      setVolume(initialVolume);
    }
  }, [initialCandles, initialVolume]);

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
      {(initialLoading) && (
        <div className="chart-overlay">
          <Spinner animation="border" />
        </div>
      )}
      {(initialError) && (
        <div className="chart-overlay text-danger">
          {initialError}
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
      {candles.length === 0 && !initialLoading && !initialError && (
        <div className="chart-overlay text-muted">
          No historical price data available yet.
        </div>
      )}
      <div className="chart-footer">
        Contract: {contractAddress} | Data points: {candles.length} | Powered by{' '}
        <a href="https://www.tradingview.com/" target="_blank" rel="noopener noreferrer">
          TradingView
        </a>
      </div>
    </div>
  );
});

export default ArtistChart;
