// src/utilities/blockchain.js

import { getWalletWeb3, getPublicWeb3 } from "./web3.js";
import { FACTORY_CONTRACT_ADDRESS } from "./config.js";
import ArtistSharesFactory from "../abis/ArtistSharesFactory.json";
import ArtistSharesToken from "../abis/ArtistSharesTokenABI.json";

const factoryAbi = ArtistSharesFactory.abi;
const tokenAbi = ArtistSharesToken.abi;

// --- 1. Get platform address (read-only) ---
export const getPlatformAddress = async () => {
  try {
    const web3 = getPublicWeb3(); // Safe — no wallet needed
    const factoryContract = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);
    const platformAddress = await factoryContract.methods.getPlatformAddress().call();
    console.log("[getPlatformAddress] Platform address:", platformAddress);
    return platformAddress;
  } catch (error) {
    console.error("[getPlatformAddress] Error:", error);
    return null;
  }
};

// --- 2. Create new artist token (requires wallet) ---
export const createArtistTokenOnFactory = async (artistId, name, symbol) => {
  try {
    const web3 = getWalletWeb3(); // ← This will prompt wallet if not connected
    const accounts = await web3.eth.getAccounts();
    const userAddress = accounts[0];

    if (!artistId?.trim()) throw new Error("Invalid artistId");
    if (!name?.trim()) throw new Error("Invalid name");
    if (!symbol?.trim()) throw new Error("Invalid symbol");

    console.log(`[createArtistTokenOnFactory] Deploying token for ${name} (${symbol})`);

    const factoryContract = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);

    const tx = await factoryContract.methods
      .createArtistToken(artistId.trim(), name.trim(), symbol.trim(), userAddress)
      .send({ from: userAddress });

    console.log("[createArtistTokenOnFactory] Transaction:", tx);

    // Try to get address from event first
    let deployedAddress = tx.events?.ArtistTokenCreated?.returnValues?.tokenAddress;

    // Fallback: query factory
    if (!deployedAddress || deployedAddress === "0x0000000000000000000000000000000000000000") {
      deployedAddress = await factoryContract.methods.getTokenByArtistId(artistId.trim()).call();
    }

    if (!deployedAddress || deployedAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Failed to retrieve deployed contract address");
    }

    console.log("[createArtistTokenOnFactory] Success! Address:", deployedAddress);
    return deployedAddress;
  } catch (error) {
    console.error("[createArtistTokenOnFactory] Error:", error);
    throw error; // Let caller (AdminPanel) handle toast/error
  }
};

// --- 3. Get artist token address by artistId (read-only) ---
export const getContractAddressFromFactory = async (artistId) => {
  try {
    if (!artistId?.trim()) {
      console.warn("[getContractAddressFromFactory] Invalid artistId");
      return null;
    }

    const web3 = getPublicWeb3();
    const factory = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);
    const address = await factory.methods.getTokenByArtistId(artistId.trim()).call();

    if (address && address !== "0x0000000000000000000000000000000000000000") {
      return address;
    }

    return null;
  } catch (error) {
    console.error("[getContractAddressFromFactory] Error:", error);
    return null;
  }
};

// --- 4. Get total supply (read-only) ---
export const getTotalSupply = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getPublicWeb3();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const totalSupply = await token.methods.totalSupply().call();
    return Number(totalSupply) / 1e18;
  } catch (error) {
    console.error("[getTotalSupply] Error:", error);
    return 0;
  }
};

// --- 5. Get current price (read-only) ---
export const getCurrentPrice = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getPublicWeb3();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const currentPrice = await token.methods.getCurrentPrice().call();
    return Number(currentPrice) / 1e8;
  } catch (error) {
    console.error("[getCurrentPrice] Error:", error);
    return 0;
  }
};

// --- 6. Get ETH/USD price (read-only) ---
export const getEthUsdPrice = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getPublicWeb3();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const price = await token.methods.getEthUsdPrice().call();
    return Number(price) / 1e8;
  } catch (error) {
    console.error("[getEthUsdPrice] Error:", error);
    return 3600; // Fallback
  }
};