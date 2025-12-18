// src/utilities/web3.js
import Web3 from "web3";
import { RPC_URL, FACTORY_CONTRACT_ADDRESS } from "./config.js"; // Uses Sepolia in prod, localhost in dev
import ArtistSharesTokenABI from "../abis/ArtistSharesTokenABI.json";
import ArtistSharesFactory from "../abis/ArtistSharesFactory.json";

// ———————— PUBLIC READ-ONLY WEB3 (no wallet needed) ————————
export const getPublicWeb3 = () => {
  // Uses RPC_URL from config.js → Sepolia in production, localhost in dev
  return new Web3(RPC_URL);
};

// ———————— WALLET-REQUIRED WEB3 (for transactions) ————————
export const getWalletWeb3 = () => {
  if (!window.ethereum) {
    throw new Error("Wallet not detected. Please connect a wallet (MetaMask, etc.) to trade.");
  }
  return new Web3(window.ethereum);
};

// Optional: Connect wallet on demand (used in trading components)
export const connectWallet = async () => {
  if (!window.ethereum) {
    throw new Error("No Ethereum wallet detected");
  }
  await window.ethereum.request({ method: "eth_requestAccounts" });
  return window.ethereum.selectedAddress;
};

// ———————— READ-ONLY: Use public RPC ————————
export const getHttpWeb3 = () => getPublicWeb3(); // Alias for backward compatibility

// Remove old WS logic unless you really need live event subscriptions
// (Most live updates come from your backend WebSocket anyway)

// ———————— READ-ONLY: Get contract address from factory ————————
export const getContractAddressFromFactory = async (artistId) => {
  try {
    if (!artistId || typeof artistId !== "string" || artistId.trim() === "") {
      console.warn("[getContractAddressFromFactory] Invalid artistId:", artistId);
      return null;
    }

    const web3 = getPublicWeb3();
    const factory = new web3.eth.Contract(ArtistSharesFactory.abi, FACTORY_CONTRACT_ADDRESS);
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

// ———————— READ-ONLY: Get token details (symbol, price, volume) ————————
export const getArtistTokenDetails = async (contractAddress) => {
  try {
    const web3 = getPublicWeb3();
    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);

    const [symbol, rawPrice, rawVolume] = await Promise.all([
      contract.methods.symbol().call(),
      contract.methods.getCurrentPrice().call(),
      contract.methods.balanceOf(contract.options.address).call(), // liquidity in curve
    ]);

    return {
      symbol,
      price: web3.utils.fromWei(rawPrice.toString(), "ether"),
      volume: formatVolume(rawVolume),
    };
  } catch (error) {
    console.error("Error fetching artist token details:", error);
    return { symbol: "N/A", price: "0", volume: "0" };
  }
};

// ———————— TRANSACTION: Deploy artist token (requires wallet) ————————
export const deployArtistContract = async (artist) => {
  try {
    const web3 = getWalletWeb3(); // ← Requires MetaMask/wallet
    const accounts = await web3.eth.getAccounts();

    if (!artist.id || !artist.name || !artist.symbol) {
      throw new Error(`Missing artist data. Provided: id=${artist.id}, name=${artist.name}, symbol=${artist.symbol}`);
    }

    const factory = new web3.eth.Contract(ArtistSharesFactory.abi, FACTORY_CONTRACT_ADDRESS);

    const tx = await factory.methods
      .createArtistToken(artist.id, artist.name, artist.symbol)
      .send({ from: accounts[0], gas: 6000000 });

    const event = tx.events.ArtistTokenCreated;
    const deployedAddress = event?.returnValues?.tokenAddress;

    if (!deployedAddress) {
      throw new Error("Contract deployed but tokenAddress not emitted");
    }

    return deployedAddress;
  } catch (error) {
    console.error("Contract deployment failed:", error);
    throw error;
  }
};

// ———————— UTILITIES ————————
export const formatVolume = (volume) => {
  const num = parseInt(volume);
  if (num >= 1_000_000) return (num / 1_000_000).toFixed(1) + "M";
  if (num >= 1_000) return (num / 1_000).toFixed(0) + "k";
  return num.toString();
};