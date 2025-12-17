import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { nodePolyfills } from 'vite-plugin-node-polyfills';

export default defineConfig({
  plugins: [
    react(),
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
    commonjsOptions: {
      defaultIsModuleExports: 'auto',  // Key option for many "default not exported" issues
      transformMixedEsModules: true,
    },
    rollupOptions: {
      external: ['@coinbase/wallet-sdk'], // optional, if it causes issues
      output: {
        manualChunks: {
          wagmi: ['wagmi', '@wagmi/connectors', '@wagmi/core'],
          web3: ['web3'],
          'react-libs': ['react', 'react-dom', 'react-router-dom'],
          viem: ['viem'],
          charts: ['chart.js', 'react-chartjs-2', 'klinecharts', 'lightweight-charts'],
          appkit: ['@reown/appkit', '@reown/appkit-adapter-wagmi'],
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