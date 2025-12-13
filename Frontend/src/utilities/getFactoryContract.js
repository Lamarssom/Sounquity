import Web3 from "web3";
import FactoryABI from "../abis/ArtistSharesFactory.json";
import { FACTORY_CONTRACT_ADDRESS } from "../config/factoryAddress";

export const getFactoryContract = async () => {
  if (window.ethereum) {
    const web3 = new Web3(window.ethereum);
    const contract = new web3.eth.Contract(FactoryABI, FACTORY_CONTRACT_ADDRESS);
    return contract;
  } else {
    throw new Error("MetaMask not detected");
  }
};