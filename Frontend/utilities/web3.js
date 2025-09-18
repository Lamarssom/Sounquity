import Web3 from "web3";
import ArtistSharesTokenABI from "../abis/ArtistSharesTokenABI.json";
import ArtistSharesFactory from "../abis/ArtistSharesFactory.json";

// Providers
const HTTP_PROVIDER_URL = "http://localhost:8545";
const WS_PROVIDER_URL = "ws://localhost:8545"; // or wss:// for remote node

// ✅ Create a single persistent WebSocketProvider instance
let sharedWsProvider = null;
const getSharedWsProvider = () => {
  if (!sharedWsProvider) {
    sharedWsProvider = new Web3.providers.WebsocketProvider(WS_PROVIDER_URL, {
      reconnect: {
        auto: true,
        delay: 5000,
        maxAttempts: 10,
      },
    });

    // Optional: auto-reconnect logic
    sharedWsProvider.on("error", (e) => {
      console.error("[WebSocket] Error:", e);
    });
    sharedWsProvider.on("end", (e) => {
      console.warn("[WebSocket] Connection ended. Reconnecting...");
      sharedWsProvider = null; 
    });
  }
  return sharedWsProvider;
};

export const getHttpWeb3 = () => new Web3(new Web3.providers.HttpProvider(HTTP_PROVIDER_URL));
export const getWsWeb3 = () => new Web3(getSharedWsProvider());
// ✅ New: Return WebSocket-based contract instance for event subscriptions
export const getWsContractInstance = (contractAddress) => {
  const wsWeb3 = getWsWeb3();
  return new wsWeb3.eth.Contract(ArtistSharesTokenABI, contractAddress);
};

// MetaMask usage for signing
export const getWeb3 = () => {
  if (!window.ethereum) {
    alert("MetaMask is required to interact with the blockchain.");
    throw new Error("MetaMask not detected");
  }
  return new Web3(window.ethereum);
};

// Factory contract address (local example)
const FACTORY_CONTRACT_ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3";

// Connect wallet
export const connectWallet = async () => {
  if (!window.ethereum) {
    alert("MetaMask not detected. Please install MetaMask.");
    throw new Error("MetaMask not detected");
  }
  await window.ethereum.request({ method: "eth_requestAccounts" });
  console.log("Wallet connected");
};

// Check wallet connection
export const checkIfWalletIsConnected = async () => {
  if (!window.ethereum) return false;
  const web3 = new Web3(window.ethereum);
  const accounts = await web3.eth.getAccounts();
  if (accounts.length === 0) {
    await connectWallet();
    return false;
  }
  return true;
};

// Format volume
export const formatVolume = (volume) => {
  const num = parseInt(volume);
  if (num >= 1_000_000) return (num / 1_000_000).toFixed(1) + "M";
  if (num >= 1_000) return (num / 1_000).toFixed(0) + "k";
  return num.toString();
};

// ✅ Use HTTP provider for read calls
export const getArtistTokenDetails = async (contractAddress) => {
  try {
    const web3 = getHttpWeb3();
    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);

    const [symbol, rawPrice, rawVolume] = await Promise.all([
      contract.methods.symbol().call(),
      contract.methods.getCurrentPrice().call(),
      contract.methods.balanceOf(contract.options.address).call(),
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

// ✅ Also use HTTP provider here for reliable factory read
export const getContractAddressFromFactory = async (artistId) => {
  try {
    const web3 = getHttpWeb3();
    const factory = new web3.eth.Contract(ArtistSharesFactory.abi, FACTORY_CONTRACT_ADDRESS);
    console.log("Getting contract address from factory for artist ID:", artistId);
    const address = await factory.methods.getTokenByArtistId(artistId).call();

    console.log("Factory returned address:", address);
    if (!address || address === "0x0000000000000000000000000000000000000000") {
      console.warn("No contract address found for artist:", artistId);
      return null;
    }

    return address;
  } catch (error) {
    console.error("Error fetching contract address from factory:", error);
    return null;
  }
};

// Keep MetaMask for writing/sending transactions
export const deployArtistContract = async (artist) => {
  try {
    const web3 = getWeb3(); // MetaMask required
    const accounts = await web3.eth.requestAccounts();

    if (!artist.id || !artist.name || !artist.symbol) {
      throw new Error(`Missing artist data. Provided: id=${artist.id}, name=${artist.name}, symbol=${artist.symbol}`);
    }

    console.log("Deploying artist contract with:", artist);

    const factory = new web3.eth.Contract(ArtistSharesFactory.abi, FACTORY_CONTRACT_ADDRESS);

    const tx = await factory.methods
      .createArtistToken(artist.id, artist.name, artist.symbol)
      .send({ from: accounts[0], gas: 6000000 });

    const event = tx.events.ArtistTokenCreated;
    const deployedAddress = event?.returnValues?.tokenAddress;

    if (!deployedAddress) {
      console.error("Deployment event not returned:", event);
      throw new Error("Contract deployed but tokenAddress not emitted");
    }

    console.log("Artist token created at:", deployedAddress);
    return deployedAddress;
  } catch (error) {
    console.error("Contract deployment failed:", error.message || error);
    return null;
  }
};