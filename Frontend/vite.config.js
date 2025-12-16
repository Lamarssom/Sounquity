import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { nodePolyfills } from 'vite-plugin-node-polyfills';
import commonjs from '@rollup/plugin-commonjs';

export default defineConfig({
  plugins: [
    react(),
    commonjs({
      // Force named exports for eventemitter3 (this fixes the default import error)
      namedExports: {
        'node_modules/eventemitter3/index.js': ['EventEmitter'],
      },
    }),
    nodePolyfills({
      globals: {
        events: true,
      },
    }),
  ],
  optimizeDeps: {
    include: ['eventemitter3', 'web3', 'wagmi', '@walletconnect/sign-client', '@walletconnect/utils', '@coinbase/wallet-sdk'],
    esbuildOptions: {
      target: 'esnext',
      supported: {
        'dynamic-import': true,
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    rollupOptions: {
      // Keep your existing external if needed, but eventemitter3 is now handled by commonjs plugin
      external: ['@coinbase/wallet-sdk'], // optional, if it causes issues
      output: {
        manualChunks: {
          wagmi: ['wagmi', '@web3modal/wagmi', '@wagmi/connectors', '@wagmi/core'],
          web3: ['web3'],
          'react-libs': ['react', 'react-dom', 'react-router-dom'],
          viem: ['viem'],
          charts: ['chart.js', 'react-chartjs-2', 'klinecharts', 'lightweight-charts'],
        },
      },
      onLog(level, log, handler) {
        if (
          log.message.includes('CJS build of Vite') ||
          log.message.includes('/#PURE/')
        ) {
          return;
        }
        handler(level, log);
      },
    },
    chunkSizeWarningLimit: 1000,
    minify: 'esbuild',
    sourcemap: false,
    target: 'esnext',
  },
});