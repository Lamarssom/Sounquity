import { createChart, CrosshairMode } from 'lightweight-charts';
import pkg from 'lightweight-charts/package.json';

const isDev = process.env.NODE_ENV === 'development';
const log = (...args) => isDev && console.log(...args);

log('[createArtistChart] 🟢 Lightweight Charts version:', pkg.version);

export const createArtistChart = (container, displayMode = 'price', formatNumberWithSuffix) => {
  if (!container) {
    log('[createArtistChart] ❌ Container is null or undefined.');
    return { chart: null, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  const width = container.clientWidth;
  const height = container.clientHeight;

  if (width === 0 || height === 0) {
    log('[createArtistChart] ⚠ Container has zero width/height:', { width, height });
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
        minMove: 0.01,
        precision: 2,
        mode: 1,
        minimumHeight: 100,
        alignLabels: true,
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
      handleScroll: {
        mouseWheel: true,
        pressedMouseMove: true,
      },
      handleScale: {
        axisPressedMouseMove: true,
        mouseWheel: true,
        pinch: true,
      },
    });
    log('[createArtistChart] 🟢 Chart initialized with size:', `${width}x${height}`);
    log('[createArtistChart] 🟢 Time scale options:', chart.timeScale().options());
  } catch (err) {
    console.error('[createArtistChart] ❌ Chart initialization failed:', err);
    return { chart: null, candleSeries: null, volumeSeries: null, cleanup: () => {} };
  }

  let lastLogTime = 0;
  const LOG_THROTTLE_MS = 10000;

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
        type: 'custom',
        formatter: (price) => {
          if (displayMode === 'marketCap') {
            if (!formatNumberWithSuffix) {
              if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
                log('[createArtistChart] ⚠ formatNumberWithSuffix not provided, using raw price');
                lastLogTime = Date.now();
              }
              return price.toFixed(2);
            }
            const formatted = formatNumberWithSuffix(price);
            if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
              log('[createArtistChart] 📊 Formatting market cap price:', { input: price, output: formatted });
              lastLogTime = Date.now();
            }
            return formatted;
          }
          const formatted = price.toFixed(2);
          if (Date.now() - lastLogTime >= LOG_THROTTLE_MS) {
            log('[createArtistChart] 📊 Formatting price:', { input: price, output: formatted });
            lastLogTime = Date.now();
          }
          return formatted;
        },
        precision: 2,
        minMove: 0.01,
      },
    });
    log('[createArtistChart] 🟢 Candlestick series created with displayMode:', displayMode);
  } catch (err) {
    console.error('[createArtistChart] ❌ Candlestick series creation failed:', err);
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
    log('[createArtistChart] 🟢 Volume series created');
  } catch (err) {
    console.error('[createArtistChart] ❌ Volume series creation failed:', err);
    return { chart, candleSeries, volumeSeries: null, cleanup: () => {} };
  }

  const resizeObserver = new ResizeObserver(() => {
    const newWidth = container.clientWidth;
    const newHeight = container.clientHeight;
    if (newWidth > 0 && newHeight > 0) {
      chart.resize(newWidth, newHeight);
      log('[createArtistChart] 🔄 Resized chart to:', `${newWidth}x${newHeight}`);
    } else {
      log('[createArtistChart] ⚠ Resize skipped due to invalid size:', { newWidth, newHeight });
    }
  });
  resizeObserver.observe(container);

  const cleanup = () => {
    log('[createArtistChart] 🧹 Cleaning up chart');
    resizeObserver.disconnect();
    if (chart) {
      chart.remove();
      log('[createArtistChart] 🟢 Chart removed');
    }
  };

  return {
    chart,
    candleSeries,
    volumeSeries,
    cleanup,
  };
};