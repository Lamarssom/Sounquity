import React from 'react';
import { createRoot } from 'react-dom/client';
import { WagmiProvider } from 'wagmi';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import { createAppKit } from '@reown/appkit';
import { WagmiAdapter } from '@reown/appkit-adapter-wagmi';
import { defineChain } from '@reown/appkit/networks';
import './index.css';
import App from './App.jsx';
import reportWebVitals from './reportWebVitals';
import { createArtistTokenOnFactory } from './utilities/blockchain';
import { Buffer } from 'buffer';

window.Buffer = window.Buffer || Buffer;
window.createArtistTokenOnFactory = createArtistTokenOnFactory;

const queryClient = new QueryClient();

const projectId = import.meta.env.VITE_WALLET_CONNECT_PROJECT_ID || 'fdc3cab70cefe145c936846e77515e92';

const networks = [
  // Keep Hardhat for local dev if needed
  defineChain({
    id: 31337,
    caipNetworkId: 'eip155:31337',
    chainNamespace: 'eip155',
    name: 'Hardhat',
    nativeCurrency: {
      name: 'Ether',
      symbol: 'ETH',
      decimals: 18,
    },
    rpcUrls: {
      default: { http: ['http://127.0.0.1:8545'] },
      public: { http: ['http://127.0.0.1:8545'] },
    },
    blockExplorers: {
      default: { name: 'Hardhat Explorer', url: 'http://localhost:8545' },
    },
    contracts: {
      multicall3: { address: '0xca11bde05977b3631167028862be2a173976ca11' },
    },
  }),

  // Add Sepolia (production chain)
  defineChain({
    id: 11155111,
    caipNetworkId: 'eip155:11155111',
    chainNamespace: 'eip155',
    name: 'Sepolia',
    nativeCurrency: {
      name: 'Ether',
      symbol: 'ETH',
      decimals: 18,
    },
    rpcUrls: {
      default: { http: ['https://ethereum-sepolia-rpc.publicnode.com', 'https://rpc.sepolia.org'] },
      public: { http: ['https://ethereum-sepolia-rpc.publicnode.com', 'https://rpc.sepolia.org'] },
    }
    blockExplorers: {
      default: { name: 'Sepolia Etherscan', url: 'https://sepolia.etherscan.io' },
    },
    // Optional: Add multicall if needed
    // contracts: { multicall3: { address: '0xca11bde05977b3631167028862be2a173976ca11' } },
  }),
];

// Test RPC connection
/*async function testRpcConnection() {
  try {
    const response = await fetch('http://127.0.0.1:8545', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        method: 'eth_chainId',
        params: [],
        id: 1,
      }),
    });
    const result = await response.json();
    // console.log('RPC chainId response:', result);
    if (result.result !== '0x7a69') {
      // console.error('Unexpected chainId, expected 31337 (0x7a69), got:', result.result);
    }
  } catch (error) {
    // console.error('Failed to connect to Hardhat RPC:', error);
  }
}*/

if (import.meta.env.DEV) {
  testRpcConnection();
}
// console.log('Networks before WagmiAdapter:', JSON.stringify(networks, null, 2));

let wagmiAdapter;
try {
  wagmiAdapter = new WagmiAdapter({
    networks,
    projectId,
    ssr: false,
  });
  // console.log('WagmiAdapter initialized:', wagmiAdapter);
} catch (error) {
  // console.error('WagmiAdapter initialization failed:', error);
  throw error;
}

try {
  createAppKit({
    adapters: [wagmiAdapter],
    networks,
    projectId,
    metadata: {
      name: 'Sounquity',
      description: 'Music investment dApp',
      url: window.location.origin,
      icons: ['https://avatars.githubusercontent.com/u/37784886'],
    },
  });
  // console.log('AppKit initialized successfully');
} catch (error) {
  // console.error('AppKit initialization failed:', error);
  throw error;
}

const config = wagmiAdapter.wagmiConfig;

const root = createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <WagmiProvider config={config}>
        <App />
      </WagmiProvider>
    </QueryClientProvider>
  </React.StrictMode>
);

reportWebVitals();