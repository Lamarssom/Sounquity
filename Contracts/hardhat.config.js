require("@nomiclabs/hardhat-ethers");
require("@nomiclabs/hardhat-waffle");
require("hardhat-gas-reporter");
require("solidity-coverage");
require("dotenv").config(); // Load environment variables from .env

module.exports = {
  solidity: {
    version:"0.8.20",
    settings: {
      optimizer: {
        enabled: true,
        runs: 1000
      }
    }
  },
  paths: {
    artifacts: "./artifacts",
    sources: "./contracts",
    cache: "./cache",
    tests: "./test",
  },
  remappings: [
    "@openzeppelin/=node_modules/@openzeppelin/",
    "@uniswap/v2-core/=node_modules/@uniswap/v2-core/",
    "@uniswap/v2-periphery/=node_modules/@uniswap/v2-periphery/",
    "@chainlink/=node_modules/@chainlink/"
  ],

  mocha: {
    timeout:120000
  },

  networks: {
    hardhat: {},

    // ✅ Add this block below
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337,
    },

    bscTestnet: {
      url: "https://data-seed-prebsc-1-s1.binance.org:8545/", // BSC Testnet RPC
      accounts: process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : [], // Load private key from .env safely
      chainId: 97, // BSC Testnet chain ID
    },

    sepolia: {
      url: "https://ethereum-sepolia-rpc.publicnode.com",
      accounts: process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : [],
      chainId: 11155111,
      gas: 3_000_000,           // enough for the factory
      gasPrice: 1_000_000_000,  // 1 gwei only → total cost ≈ 0.003 ETH
    },
  },
};