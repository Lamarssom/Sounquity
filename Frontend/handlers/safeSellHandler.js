import ArtistSharesToken from '../abis/ArtistSharesTokenABI.json';
import { fetchEthPrice } from '../utilities/fetchEthPrice';
const ArtistSharesTokenABI = ArtistSharesToken.abi;

export async function safeSellHandler(web3, contractAddress, userAddress, dollarAmount) {
  const isDev = process.env.NODE_ENV === "development";
  const log = (...args) => isDev && console.log(...args);
  try {
    // Validate inputs
    if (!dollarAmount || isNaN(dollarAmount) || dollarAmount <= 0) {
      throw new Error(`Invalid dollarAmount: ${dollarAmount}`);
    }

    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);

    // Fetch contract state
    const [
      totalSupplyRaw,
      contractBalanceRaw,
      userBalanceRaw,
      currentPriceRaw,
      dailyTradesRaw,
      dailyLimitRaw
    ] = await Promise.all([
      contract.methods.totalSupply().call(),
      contract.methods.balanceOf(contractAddress).call(),
      contract.methods.balanceOf(userAddress).call(),
      contract.methods.getCurrentPrice().call(),
      contract.methods.dailyTrades(userAddress).call(),
      contract.methods.dailyLimit().call()
    ]);

    // Convert to numbers
    const totalSupplyNum = parseFloat(web3.utils.fromWei(totalSupplyRaw, "ether"));
    const contractBalanceNum = parseFloat(web3.utils.fromWei(contractBalanceRaw, "ether"));
    const userBalanceNum = parseFloat(web3.utils.fromWei(userBalanceRaw, "ether"));
    const currentPriceWei = BigInt(currentPriceRaw);
    const dailyTradesWei = BigInt(dailyTradesRaw);
    const dailyLimitWei = BigInt(dailyLimitRaw);

    // Fetch ETH price
    const ethPriceInUsd = await fetchEthPrice(web3, contractAddress);
    if (ethPriceInUsd <= 0 || isNaN(ethPriceInUsd)) {
      throw new Error(`Invalid ETH price: ${ethPriceInUsd}`);
    }

    // Calculate price per token
    const pricePerTokenEth = Number(web3.utils.fromWei(currentPriceWei, "ether"));
    const pricePerTokenUsd = pricePerTokenEth * ethPriceInUsd;
    if (pricePerTokenUsd <= 0 || pricePerTokenUsd > 1000) {
      log("Invalid pricePerTokenUsd:", pricePerTokenUsd, "currentPriceWei:", currentPriceWei.toString());
      throw new Error(`Invalid price per token (USD): ${pricePerTokenUsd}`);
    }

    // Calculate amount
    let amount = Math.floor(dollarAmount / pricePerTokenUsd);
    log("Initial amount:", amount, "Dollar amount:", dollarAmount, "Price per token (USD):", pricePerTokenUsd);

    // Cap amount
    if (amount > userBalanceNum) {
      amount = Math.floor(userBalanceNum);
      log("Capped amount to user balance:", amount);
    }

    // Ensure amount is positive
    if (amount <= 0) {
      log("Amount after capping:", amount, "User balance:", userBalanceNum, "Raw balance:", userBalanceRaw);
      throw new Error("Amount must be > 0");
    }

    // Calculate total return
    const totalReturn = BigInt(amount) * currentPriceWei;
    const fee = (totalReturn * 2n) / 100n;
    const payout = totalReturn - fee;
    const payoutEth = Number(web3.utils.fromWei(payout, "ether"));
    const payoutUsd = payoutEth * ethPriceInUsd;

    const contractEthBalance = BigInt(await web3.eth.getBalance(contractAddress));

    // Logs
    log("Total Supply:", totalSupplyNum);
    log("Contract Available Shares:", contractBalanceNum);
    log("User Current Balance:", userBalanceNum);
    log("Raw User Balance (wei):", userBalanceRaw);
    log("Current Price (wei):", currentPriceWei.toString());
    log("Dollar Amount ($):", dollarAmount);
    log("Price per token (ETH):", pricePerTokenEth);
    log("Price per token (USD):", pricePerTokenUsd);
    log("Calculated Amount:", amount);
    log("Total Return (wei):", totalReturn.toString());
    log("Fee (wei):", fee.toString());
    log("Payout (wei):", payout.toString());
    log("Payout (USD):", payoutUsd);
    log("Daily Trades (wei):", dailyTradesWei.toString());
    log("Daily Limit (wei):", dailyLimitWei.toString());
    log("Contract ETH Balance (wei):", contractEthBalance.toString());
    log("ETH Price ($):", ethPriceInUsd);
    log("✅ DEBUG ADDRESSES");
    log("User address:", userAddress);
    log("Contract address:", contractAddress);

    // Safety checks
    if (BigInt(userBalanceRaw) < BigInt(amount) * BigInt(10**18)) throw new Error("Not enough shares to sell");
    if (fee > totalReturn) throw new Error("Fee exceeds total return");
    if (dailyTradesWei + payout > dailyLimitWei) throw new Error("Exceeds daily trade limit");
    if (contractEthBalance < payout) throw new Error("Insufficient contract balance for payout");

    return {
      success: true,
      amount: amount.toString(),
      totalReturn: totalReturn.toString(),
      payout: payout.toString(),
      price: currentPriceWei.toString()
    };
  } catch (error) {
    console.error("❌ Sell pre-check failed:", error);
    return { success: false, error: error?.message || error.toString() };
  }
}