// src/blockchain.js
import Web3 from "web3";
import { FACTORY_CONTRACT_ADDRESS } from "../utilities/config.js";
import ArtistSharesFactory from "../abis/ArtistSharesFactory.json";
import ArtistSharesToken from "../abis/ArtistSharesTokenABI.json";

// Initialize Web3 safely
const getWeb3Instance = () => {
  if (window.ethereum) {
    return new Web3(window.ethereum);
  }
  if (window.web3 && window.web3.currentProvider) {
    return new Web3(window.web3.currentProvider);
  }
  
  // Fallback: Use public RPC for read-only (no signing)
  console.warn("No wallet detected. Using read-only public RPC.");
  return new Web3(TESTNET_RPC_URL);  // or a fallback like "https://ethereum-sepolia-rpc.publicnode.com"
};

// Load the contract ABIs
const factoryAbi = ArtistSharesFactory.abi;
const tokenAbi = ArtistSharesToken.abi;

// --- 1. Get platform address ---
export const getPlatformAddress = async () => {
  try {
    const web3 = getWeb3Instance();
    const factoryContract = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);
    const platformAddress = await factoryContract.methods.getPlatformAddress().call();
    console.log("[getPlatformAddress] Platform address:", platformAddress);
    return platformAddress;
  } catch (error) {
    console.error("[getPlatformAddress] Error:", error);
    return null;
  }
};

// --- 2. Create new artist token ---
export const createArtistTokenOnFactory = async (artistId, name, symbol) => {
  try {
    const web3 = getWeb3Instance();
    let accounts = await web3.eth.getAccounts();

    if (!window.ethereum) {
      throw new Error("Please install MetaMask to create an artist token.");
    }

    if (!accounts || accounts.length === 0) {
      await window.ethereum.request({ method: "eth_requestAccounts" });
      accounts = await web3.eth.getAccounts();
    }

    const userAddress = accounts[0];
    const factoryContract = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);

    if (!artistId || typeof artistId !== "string" || artistId.trim() === "") {
      throw new Error("Invalid artistId");
    }
    if (!name || typeof name !== "string" || name.trim() === "") {
      throw new Error("Invalid name");
    }
    if (!symbol || typeof symbol !== "string" || symbol.trim() === "") {
      throw new Error("Invalid symbol");
    }

    console.log(`[createArtistTokenOnFactory] Creating token for artist ${artistId} - ${name} with symbol ${symbol}`);

    const teamWallet = userAddress;
    const tx = await factoryContract.methods
      .createArtistToken(artistId, name, symbol, teamWallet)
      .send({ from: userAddress });

    console.log("[createArtistTokenOnFactory] Transaction result:", tx);

    const deployedAddress = tx.events?.ArtistTokenCreated?.returnValues?.tokenAddress;
    if (deployedAddress && deployedAddress !== "0x0000000000000000000000000000000000000000") {
      console.log("[createArtistTokenOnFactory] Deployed address:", deployedAddress);
      return deployedAddress;
    }

    const fallbackAddress = await factoryContract.methods.getTokenByArtistId(artistId).call();
    if (fallbackAddress && fallbackAddress !== "0x0000000000000000000000000000000000000000") {
      console.log("[createArtistTokenOnFactory] Fallback address:", fallbackAddress);
      return fallbackAddress;
    }

    throw new Error("Contract address not found after creation.");
  } catch (error) {
    console.error("[createArtistTokenOnFactory] Error:", error);
    throw error;
  }
};

// --- 3. Get artist token address using artist ID ---
export const getContractAddressFromFactory = async (artistId) => {
  try {
    if (!artistId || typeof artistId !== "string" || artistId.trim() === "") {
      console.warn("[getContractAddressFromFactory] Invalid artistId:", artistId);
      return null;
    }

    const web3 = getWeb3Instance();
    const factory = new web3.eth.Contract(factoryAbi, FACTORY_CONTRACT_ADDRESS);
    const trimmedArtistId = artistId.trim();

    console.log("[getContractAddressFromFactory] Fetching address for artistId:", trimmedArtistId);

    const address = await factory.methods.getTokenByArtistId(trimmedArtistId).call();
    if (address && address !== "0x0000000000000000000000000000000000000000") {
      console.log("[getContractAddressFromFactory] Found contract address:", address);
      return address;
    }

    console.warn("[getContractAddressFromFactory] No contract found for artistId:", trimmedArtistId);
    return null;
  } catch (error) {
    console.error("[getContractAddressFromFactory] Error:", error);
    return null;
  }
};

// --- 4. Get total supply ---
export const getTotalSupply = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getWeb3Instance();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const totalSupply = await token.methods.totalSupply().call();
    // Convert string to number, assuming 18 decimals
    return Number(totalSupply) / 1e18;
  } catch (error) {
    console.error("[getTotalSupply] Error:", error);
    throw error;
  }
};

// --- 5. Get current price ---
export const getCurrentPrice = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getWeb3Instance();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const currentPrice = await token.methods.getCurrentPrice().call();
    // Convert string to number, assuming 8 decimals
    return Number(currentPrice) / 1e8;
  } catch (error) {
    console.error("[getCurrentPrice] Error:", error);
    throw error;
  }
};

// --- 6. Get ETH/USD price ---
export const getEthUsdPrice = async (contractAddress) => {
  try {
    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
      throw new Error("Invalid contract address");
    }
    const web3 = getWeb3Instance();
    const token = new web3.eth.Contract(tokenAbi, contractAddress);
    const price = await token.methods.getEthUsdPrice().call();
    console.log("[getEthUsdPrice] ETH/USD Price:", price);
    return Number(price) / 1e8; // Convert from 8 decimals to USD
  } catch (error) {
    console.error("[getEthUsdPrice] Error:", error);
    return 3600; // Fallback to $3600
  }
};
