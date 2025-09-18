import ArtistSharesToken from '../abis/ArtistSharesTokenABI.json';
import { fetchEthPrice } from '../utilities/fetchEthPrice';
const ArtistSharesTokenABI = ArtistSharesToken.abi;

export async function safeBuyHandler(web3, contractAddress, userAddress, dollarAmount) {
  const isDev = process.env.NODE_ENV === "development";
  const log = (...args) => isDev && console.log(...args);
  try {
    if (!dollarAmount || isNaN(dollarAmount) || dollarAmount <= 0) {
      throw new Error(`Invalid dollarAmount: ${dollarAmount}`);
    }

    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);
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

    const totalSupplyNum = parseFloat(web3.utils.fromWei(totalSupplyRaw, "ether"));
    const contractBalanceNum = parseFloat(web3.utils.fromWei(contractBalanceRaw, "ether"));
    const userBalanceNum = parseFloat(web3.utils.fromWei(userBalanceRaw, "ether"));
    const currentPriceWei = BigInt(currentPriceRaw);
    const dailyTradesWei = BigInt(dailyTradesRaw);
    const dailyLimitWei = BigInt(dailyLimitRaw);

    if (currentPriceWei <= 0 || currentPriceWei > BigInt(10**18)) {
      throw new Error(`Invalid currentPriceWei: ${currentPriceWei}`);
    }

    const ethPriceInUsd = await fetchEthPrice(web3, contractAddress);
    if (ethPriceInUsd <= 0 || isNaN(ethPriceInUsd)) {
      throw new Error(`Invalid ETH price: ${ethPriceInUsd}`);
    }

    const pricePerTokenEth = Number(web3.utils.fromWei(currentPriceWei, "ether"));
    const pricePerTokenUsd = pricePerTokenEth * ethPriceInUsd;
    if (pricePerTokenUsd <= 0 || pricePerTokenUsd > 1000) {
      log("Invalid pricePerTokenUsd:", pricePerTokenUsd, "currentPriceWei:", currentPriceWei.toString());
      throw new Error(`Invalid price per token (USD): ${pricePerTokenUsd}`);
    }

    let amount = Math.floor(dollarAmount / pricePerTokenUsd);
    log("Initial amount:", amount, "Dollar amount:", dollarAmount, "Price per token (USD):", pricePerTokenUsd);

    const maxHolding = totalSupplyNum * 0.05;
    if (amount > contractBalanceNum) {
      amount = Math.floor(contractBalanceNum);
      log("Capped amount to available shares:", amount);
    }
    if (userBalanceNum + amount > maxHolding) {
      amount = Math.floor(maxHolding - userBalanceNum);
      log("Capped amount to max holding:", amount);
    }

    if (amount <= 0) {
      log("Amount after capping:", amount, "User balance:", userBalanceNum, "Raw balance:", userBalanceRaw);
      throw new Error("Amount must be > 0");
    }

    const totalCost = (BigInt(amount) * currentPriceWei).toString();
    const fee = (BigInt(totalCost) * 2n) / 100n;
    const totalAfterFee = (BigInt(totalCost) - fee).toString();
    const totalAfterFeeEth = Number(web3.utils.fromWei(totalAfterFee, "ether"));
    const totalAfterFeeUsd = totalAfterFeeEth * ethPriceInUsd;

    const remainingDailyLimit = dailyLimitWei - dailyTradesWei;

    log("Total Supply:", totalSupplyNum);
    log("Contract Available Shares:", contractBalanceNum);
    log("User Current Balance:", userBalanceNum);
    log("Raw User Balance (wei):", userBalanceRaw);
    log("Current Price (wei):", currentPriceWei.toString());
    log("Dollar Amount ($):", dollarAmount);
    log("Price per token (ETH):", pricePerTokenEth);
    log("Price per token (USD):", pricePerTokenUsd);
    log("Calculated Amount:", amount);
    log("Total Cost (wei):", totalCost);
    log("Fee (wei):", fee.toString());
    log("Total After Fee (wei):", totalAfterFee);
    log("Total After Fee (USD):", totalAfterFeeUsd);
    log("Daily Trades (wei):", dailyTradesWei.toString());
    log("Daily Limit (wei):", dailyLimitWei.toString());
    log("Remaining Daily Limit (wei):", remainingDailyLimit.toString());
    log("ETH Price ($):", ethPriceInUsd);
    log("✅ DEBUG ADDRESSES");
    log("User address:", userAddress);
    log("Contract address:", contractAddress);
    log("Expected msg.value (wei):", totalCost);

    if (BigInt(userBalanceRaw) + BigInt(amount) * BigInt(10**18) > BigInt(maxHolding) * BigInt(10**18)) {
      throw new Error(`Exceeds max holding limit: Current balance (${userBalanceNum}) + Requested (${amount}) > Max limit (${maxHolding})`);
    }
    if (BigInt(contractBalanceRaw) < BigInt(amount) * BigInt(10**18)) {
    throw new Error("Not enough shares available");
}
if (dailyTradesWei + BigInt(totalCost) > dailyLimitWei) {
    throw new Error("Exceeds daily trade limit");
}

return {
    success: true,
    amount: amount.toString(),
    totalCost: totalCost,
    price: currentPriceWei.toString()
};
} catch (error) {
console.error("❌ Buy pre-check failed:", error);
return { success: false, error: error?.message || error.toString() };
}
}