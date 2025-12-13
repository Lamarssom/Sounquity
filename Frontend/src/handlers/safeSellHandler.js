import axios from 'axios';
import rawAbi from "../abis/ArtistSharesTokenABI.json";
const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

export async function safeSellHandler(web3, contractAddress, userAddress, dollarAmount, slippage) {
  const isDev = process.env.NODE_ENV === "development";
  const log = (...args) => isDev && console.log(...args);
  try {
    if (!dollarAmount || isNaN(dollarAmount) || dollarAmount <= 0) {
      throw new Error(`Invalid dollarAmount: ${dollarAmount}`);
    }
    if (!slippage || isNaN(slippage) || slippage < 0 || slippage > 100) {
      throw new Error(`Invalid slippage: ${slippage}`);
    }

    const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);
    const [
      userBalanceRaw,
      priceMicroRaw,
      dailySellLimitUsdRaw,
      ethUsdPriceRaw
    ] = await Promise.all([
      contract.methods.balanceOf(userAddress).call(),
      contract.methods.getCurrentPriceMicroUSD().call(), 
      contract.methods.dailySellLimitUsd().call(),
      contract.methods.getEthUsdPrice().call()
    ]);

    const userBalanceWei = BigInt(userBalanceRaw);
    const priceMicro = BigInt(priceMicroRaw);
    const ethUsdRaw = BigInt(ethUsdPriceRaw);
    const ethUsd = Number(ethUsdRaw) / 1e8;
    const dailySellLimitUsd = BigInt(dailySellLimitUsdRaw);

    // --- CORRECT PRICE IN WEI ---
    const priceUsd = Number(priceMicro) / 1e8;
    const currentPriceWei = ethUsd > 0 
      ? BigInt(Math.floor(priceUsd * 1e18 / ethUsd))
      : 0n;

    if (currentPriceWei === 0n) {
      throw new Error("Cannot sell: Price is zero");
    }

    // === CORRECT: Use marginal price (what 1 token actually sells for) ===
    const oneTokenWei = 1000000000000000000n;
    const ethOutForOneRaw = await contract.methods.getEthForTokens(oneTokenWei.toString()).call();
    const ethOutForOne = BigInt(ethOutForOneRaw);
    const ethOutForOneFloat = Number(ethOutForOne) / 1e18;
    const marginalPriceUsd = ethOutForOneFloat * ethUsd;

    if (marginalPriceUsd <= 0) {
      throw new Error("Marginal price is zero");
    }

    log("MARGINAL PRICE DEBUG:", {
      ethOutForOne: ethOutForOne.toString(),
      ethUsd,
      marginalPriceUsd
    });

    let amount = Math.floor(dollarAmount / marginalPriceUsd);
    if (amount > Number(userBalanceWei) / 1e18) {
      amount = Math.floor(Number(userBalanceWei) / 1e18);
    }
    if (amount <= 0) {
      throw new Error("Amount must be > 0");
    }

    const tokensInWei = BigInt(amount) * 1000000000000000000n;
    const ethOutRaw = await contract.methods.getEthForTokens(tokensInWei.toString()).call();
    const ethOutWei = BigInt(ethOutRaw);
    const feeBps = BigInt(await contract.methods.calculateSellFee(tokensInWei.toString()).call());
    const fee = (ethOutWei * feeBps) / 10000n;
    const payoutWei = ethOutWei - fee;
    const payoutUsd = Number(payoutWei) / 1e18 * ethUsd;

    const minEthOut = BigInt(Math.floor(Number(payoutWei) * (1 - slippage / 100)));
    const contractEthBalance = BigInt(await web3.eth.getBalance(contractAddress));

    let dailyTradeVolumeUsd;
    try {
      const response = await axios.get(
        `${import.meta.env.VITE_API_URL}/api/blockchain/financials/by-user/${userAddress}`,
        { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
      );
      dailyTradeVolumeUsd = BigInt(Math.floor(response.data * 1e8));
    } catch (err) {
      log("Failed to fetch daily trade volume:", err.message);
      throw new Error("Unable to verify daily trade limit");
    }

    const totalReturnWei = ethOutWei;  // ← FIXED: was undefined

    log("SELL DEBUG:", {
      dollarAmount,
      marginalPriceUsd,
      amount,
      tokensInWei: tokensInWei.toString(),
      totalReturnWei: totalReturnWei.toString(),
      fee: fee.toString(),
      payoutWei: payoutWei.toString(),
      payoutUsd,
      minEthOut: minEthOut.toString(),
      contractEthBalance: contractEthBalance.toString(),
      dailySellLimitUsd: dailySellLimitUsd.toString(),
      dailyTradeVolumeUsd: dailyTradeVolumeUsd.toString()
    });

    if (userBalanceWei < tokensInWei) {
      throw new Error("Not enough shares to sell");
    }
    if (fee > totalReturnWei) {
      throw new Error("Fee exceeds return");
    }
    if (dailyTradeVolumeUsd + BigInt(Math.floor(payoutUsd * 1e8)) > dailySellLimitUsd) {
      throw new Error("Exceeds daily trade limit");
    }
    if (contractEthBalance < payoutWei) {
      throw new Error("Insufficient contract ETH");
    }

    return {
      success: true,
      amount: tokensInWei.toString(),
      totalReturn: ethOutWei.toString(),
      payout: payoutWei.toString(),
      minEthOut: minEthOut.toString(),
      price: currentPriceWei.toString()
    };
  } catch (error) {
    console.error("Sell pre-check failed:", error);
    return { success: false, error: error?.message || error.toString() };
  }
}