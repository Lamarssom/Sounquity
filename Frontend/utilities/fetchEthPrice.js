import rawAbi from '../abis/ArtistSharesTokenABI.json';
const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

export async function fetchEthPrice(web3, contractAddress) {
  try {
    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);
    const ethPriceRaw = await contract.methods.getEthUsdPrice().call();
    const ethPrice = Number(ethPriceRaw) / 10**8; // Chainlink returns 8 decimals
  //  console.log("Fetched ETH/USD price:", ethPrice);
    return ethPrice;
  } catch (error) {
    console.error("Failed to fetch ETH price:", error);
    return 3500; // Fallback for testing
  }
}