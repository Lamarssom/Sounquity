// Local Hardhat (for development)
const LOCAL_FACTORY_ADDRESS = "0xCf7Ed3AccA5a467e9e704C703E8D87F634fB0Fc9";
const LOCAL_CHAIN_ID = 31337;

// Sepolia Testnet (live demo)
const TESTNET_FACTORY_ADDRESS = "0xE79d7Fe208E6ece678fb680377a7957Aa466a0f6";
const TESTNET_CHAIN_ID = 11155111;
const TESTNET_CHAIN_NAME = "sepolia";
const TESTNET_RPC_URL = "https://ethereum-sepolia-rpc.publicnode.com";
const TESTNET_EXPLORER = "https://sepolia.etherscan.io";

// Backend URL (use env var in production, fallback to localhost)
const BACKEND_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// Auto-detect environment
const isProduction = import.meta.env.PROD;  // Vercel sets this to true in production

export const FACTORY_CONTRACT_ADDRESS = isProduction 
  ? TESTNET_FACTORY_ADDRESS 
  : LOCAL_FACTORY_ADDRESS;

export const CHAIN_ID = isProduction ? TESTNET_CHAIN_ID : LOCAL_CHAIN_ID;
export const CHAIN_NAME = isProduction ? TESTNET_CHAIN_NAME : "localhost";
export const RPC_URL = isProduction ? TESTNET_RPC_URL : "http://127.0.0.1:8545";
export const EXPLORER_URL = isProduction ? TESTNET_EXPLORER : "http://localhost:8545";
export const API_BASE_URL = BACKEND_URL;