import Web3 from "../utilities/web3.js";
import ArtistSharesToken from "../abis/ArtistSharesTokenABI.json";
import { getContractAddressFromFactory } from "../utilities/blockchain.js";

let web3;
let accounts = [];
let isWeb3Initialized = false;
let contractInstances = {}; // Cache per artist

// Initialize Web3
export const initWeb3 = async () => {
  if (window.ethereum && !isWeb3Initialized) {
    try {
      await window.ethereum.request({ method: "eth_requestAccounts" });
      web3 = new Web3(window.ethereum);
      accounts = await web3.eth.getAccounts();
      isWeb3Initialized = true;
    } catch (error) {
      console.error("User denied account access:", error);
      throw error;
    }
  } else if (!window.ethereum) {
    throw new Error("MetaMask not detected. Please install MetaMask.");
  }
};

export const getCurrentAccount = () => accounts[0];

// Get Contract Instance for a specific artist
export const getContract = async (artistId) => {
  if (!web3) await initWeb3();

  if (contractInstances[artistId]) {
    return contractInstances[artistId];
  }

  const contractAddress = await getContractAddressFromFactory(artistId);
  if (!contractAddress) {
    throw new Error(`No contract address found for artistId: ${artistId}`);
  }

  const contract = new web3.eth.Contract(ArtistSharesToken.abi, contractAddress);
  contractInstances[artistId] = contract;
  return contract;
};

// Buy Shares
export const buyShares = async (artistId, amount) => {
  try {
    const contract = await getContract(artistId);
    const buyer = getCurrentAccount();

    const pricePerShare = await contract.methods.getCurrentPrice().call();
    const fee = (pricePerShare * 2) / 100;
    const totalPrice = web3.utils.toBN(pricePerShare).mul(web3.utils.toBN(amount));
    const totalAfterFee = totalPrice.sub(web3.utils.toBN(fee));

    console.log(`Buying ${amount} shares at ${pricePerShare} Wei each.`);

    const tx = await contract.methods.buyShares(amount).send({
      from: buyer,
      value: totalAfterFee.toString(),
      gas: 500000,
    });

    console.log("Transaction hash:", tx.transactionHash);
    return tx.transactionHash;
  } catch (error) {
    console.error("Error buying shares:", error.message || error);
    throw error;
  }
};

// Sell Shares
export const sellShares = async (artistId, amount) => {
  try {
    const contract = await getContract(artistId);
    const seller = getCurrentAccount();

    const tx = await contract.methods.sellShares(amount).send({
      from: seller,
      gas: 500000,
    });

    console.log("Transaction hash:", tx.transactionHash);
    return tx.transactionHash;
  } catch (error) {
    console.error("Error selling shares:", error.message || error);
    throw error;
  }
};

// List Shares for Sale
export const listSharesForSale = async (artistId, amount, pricePerShare) => {
  try {
    const contract = await getContract(artistId);
    const priceInWei = web3.utils.toWei(pricePerShare.toString(), "ether");

    const tx = await contract.methods.listSharesForSale(amount, priceInWei).send({
      from: accounts[0],
      gas: 500000,
    });

    console.log("Transaction hash:", tx.transactionHash);
    return tx.transactionHash;
  } catch (error) {
    console.error("Error listing shares:", error.message || error);
    throw error;
  }
};

// Get Current Price
export const getCurrentPrice = async (artistId) => {
  try {
    const contract = await getContract(artistId);
    const price = await contract.methods.getCurrentPrice().call();
    return web3.utils.fromWei(price, "ether");
  } catch (error) {
    console.error("Error getting price:", error.message || error);
    throw error;
  }
};