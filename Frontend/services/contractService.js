import Web3 from "web3";
import ArtistSharesTokenABI from "../abis/ArtistSharesTokenABI.json"; // Correct path to the ABI file

const web3 = new Web3(window.ethereum);  // Assuming MetaMask is available

const contractService = {
  // Initialize contract
  getContract: (contractAddress) => {
    return new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);
  },

  // Get artist price and volume
  getArtistPriceAndVolume: async (contractAddress) => {
    try {
      const contract = contractService.getContract(contractAddress);
      const price = await contract.methods.getArtistPrice().call();
      const volume = await contract.methods.getArtistVolume().call();
      return {
        price: web3.utils.fromWei(price, "ether"), // Convert to human-readable format
        volume,
      };
    } catch (error) {
      console.error("Error fetching price and volume:", error);
      throw error;
    }
  },

  // Buy shares
  buyShares: async (contractAddress, amount, fromAddress) => {
    try {
      const contract = contractService.getContract(contractAddress);
      const price = await contract.methods.getArtistPrice().call();
      const totalCost = web3.utils.toWei((price * amount).toString(), "ether"); // Price for the amount of shares

      await contract.methods.buyShares(amount).send({ from: fromAddress, value: totalCost });
      console.log("Shares bought successfully!");
    } catch (error) {
      console.error("Error buying shares:", error);
      throw error;
    }
  },

  // Sell shares
  sellShares: async (contractAddress, amount, fromAddress) => {
    try {
      const contract = contractService.getContract(contractAddress);
      await contract.methods.sellShares(amount).send({ from: fromAddress });
      console.log("Shares sold successfully!");
    } catch (error) {
      console.error("Error selling shares:", error);
      throw error;
    }
  },

  // List shares for sale
  listSharesForSale: async (contractAddress, amount, price, fromAddress) => {
    try {
      const contract = contractService.getContract(contractAddress);
      await contract.methods.listSharesForSale(amount, web3.utils.toWei(price.toString(), "ether")).send({ from: fromAddress });
      console.log("Shares listed for sale successfully!");
    } catch (error) {
      console.error("Error listing shares for sale:", error);
      throw error;
    }
  },
};

export default contractService;