// src/utilities/createArtistChart.js
import { createChart, CrosshairMode } from 'lightweight-charts';
import pkg from 'lightweight-charts/package.json';

const isDev = process.env.NODE_ENV === 'development';
const log = (...args) => isDev && console.log(...args);

log('[createArtistChart] Lightweight Charts version:', pkg.version);

export const createArtistChart = (container, displayMode = 'price', formatNumberWithSuffix) => {
  if (!container) {
    log('[createArtistChart] Container is null or undefined.');
    return { chart: null, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  const width = container.clientWidth;
  const height = container.clientHeight;

  if (width === 0 || height === 0) {
    log('[createArtistChart] Container has zero width/height:', { width, height });
    return { chart: null, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  let chart;
  try {
    chart = createChart(container, {
      width,
      height,
      layout: {
        background: { color: '#1C2526' },
        textColor: '#FFFFFF',
      },
      grid: {
        vertLines: { color: '#2D3A3E', style: 1 },
        horzLines: { color: '#2D3A3E', style: 1 },
      },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: {
        borderColor: '#2D3A3E',
        autoScale: true,
        scaleMargins: { top: 0.2, bottom: 0.2 },
        // CRITICAL: 8-digit precision
        precision: 8,
        minMove: 0.00000001,
      },
      timeScale: {
        borderColor: '#2D3A3E',
        barSpacing: 5,
        minBarSpacing: 0.1,
        maxBarSpacing: 500,
        lockVisibleTimeRangeOnResize: true,
        timeVisible: true,
        secondsVisible: true,
        fixLeftEdge: false,
        fixRightEdge: false,
        allowShiftVisibleRangeOnWhitespace: true,
        tickMarkFormatter: (time) => {
          const date = new Date(time * 1000);
          return date.toLocaleTimeString('en-US', { hour12: false, timeZone: 'UTC' });
        },
      },
      handleScroll: { mouseWheel: true, pressedMouseMove: true },
      handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
    });
    log('[createArtistChart] Chart initialized:', `${width}x${height}`);
  } catch (err) {
    console.error('[createArtistChart] Chart init failed:', err);
    return { chart: null, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  let candleSeries;
  try {
    candleSeries = chart.addCandlestickSeries({
      upColor: '#26A69A',
      downColor: '#EF5350',
      borderVisible: false,
      wickUpColor: '#26A69A',
      wickDownColor: '#EF5350',
      noChangeColor: '#888888',
      priceFormat: {
        type: 'price',
        precision: 10,
        minMove: 0.0000000001,
      },
      wickVisible: true,
      borderWidth: 2,
      lastValueVisible: true
    });
    log('[createArtistChart] Candlestick series created');
  } catch (err) {
    console.error('[createArtistChart] Candlestick failed:', err);
    return { chart, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  let volumeSeries;
  try {
    volumeSeries = chart.addHistogramSeries({
      priceFormat: { type: 'volume', precision: 0, minMove: 1 },
      priceScaleId: 'volume',
      scaleMargins: { top: 0.7, bottom: 0 },
      autoScale: true,
      baseLineVisible: true,
      baseLineWidth: 1,
      baseLineColor: '#2D3A3E',
    });

    chart.priceScale('volume').applyOptions({
      scaleMargins: { top: 0.7, bottom: 0 },
      autoScale: true,
      mode: 0,
      minMove: 1,
      precision: 0,
    });
    log('[createArtistChart] Volume series created');
  } catch (err) {
    console.error('[createArtistChart] Volume series failed:', err);
    return { chart, candleSeries, volumeSeries: null, cleanup: () => {} };
  }

  const resizeObserver = new ResizeObserver(() => {
    const newWidth = container.clientWidth;
    const newHeight = container.clientHeight;
    if (newWidth > 0 && newHeight > 0) {
      chart.resize(newWidth, newHeight);
    }
  });
  resizeObserver.observe(container);

  const cleanup = () => {
    resizeObserver.disconnect();
    if (chart) chart.remove();
  };

  return { chart, candleSeries, volumeSeries, cleanup };
};