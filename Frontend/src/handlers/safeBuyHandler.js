// safeBuyHandler.js
import rawAbi from '../abis/ArtistSharesTokenABI.json';
const ArtistSharesTokenABI = rawAbi.abi || rawAbi;
import axios from 'axios';               // <-- make sure axios is imported

export async function safeBuyHandler(web3, contractAddress, userAddress, dollarAmount, slippage) {
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

    // 1. On-chain state
    const [
      tokensInCurveRaw,
      userBalanceRaw,
      priceMicroRaw,
      ethUsdPriceRaw,
      buyFeeRaw
    ] = await Promise.all([
      contract.methods.tokensInCurve().call(),
      contract.methods.balanceOf(userAddress).call(),
      contract.methods.getCurrentPriceMicroUSD().call(),
      contract.methods.getEthUsdPrice().call(),
      contract.methods.BUY_FEE().call()
    ]);

    const tokensInCurveWei = BigInt(tokensInCurveRaw);
    const userBalanceWei   = BigInt(userBalanceRaw);
    const priceMicro       = BigInt(priceMicroRaw);
    const ethUsdRaw        = BigInt(ethUsdPriceRaw);
    const buyFeeBps        = BigInt(buyFeeRaw);

    const ethUsd = Number(ethUsdRaw) / 1e8;
    let priceUsd = Number(priceMicro) / 1e8;

    // 2. FALLBACK: if price is 0 → estimate from curve  (MOVED UP)
    if (priceUsd === 0 && tokensInCurveWei > 0) {
      const TARGET_FDV_USD = 1000;
      const TOTAL_SUPPLY   = 1_000_000_000;
      const progress = Number(tokensInCurveWei) / 1e18 / TOTAL_SUPPLY;
      priceUsd = progress * TARGET_FDV_USD / TOTAL_SUPPLY;
      log("ZERO PRICE FALLBACK → curve estimate:", {
        tokensInCurve: tokensInCurveWei.toString(),
        progress: progress.toFixed(6),
        estimatedPriceUsd: priceUsd.toFixed(8)
      });
    }

    if (priceUsd <= 0) {
      throw new Error("Price is zero – cannot buy");
    }

    // 3. Convert $ → ETH (including BUY_FEE)
    const usdAmount = dollarAmount;                                   // $50
    const ethPriceUsd = Number(ethUsdRaw) / 1e8;                       // e.g. 3500
    const ethBeforeFee = usdAmount / ethPriceUsd;                     // 0.0142857 ETH
    const ethAfterFee = ethBeforeFee * 10000 / (10000 - Number(buyFeeBps));
    let ethInWei = web3.utils.toWei(ethAfterFee.toFixed(18), 'ether');

    // 4. How many tokens do we get for that ETH?
    let tokensOutWei = BigInt(
      await contract.methods.getTokensForEth(ethInWei).call()
    );

    // PASTE THIS BLOCK BELOW
    const priceMicroRawAfter = await contract.methods.getCurrentPriceMicroUSD().call();
    const priceMicroAfter = BigInt(priceMicroRawAfter);
    const priceUsdAfter = Number(priceMicroAfter) / 1e8;
    log("REAL PRICE AFTER BUY:", { priceUsd: priceUsdAfter.toFixed(10) });

    // 5. Apply slippage
    const minTokensOutWei = (tokensOutWei * BigInt(100 - slippage)) / 100n;

    // === ESTIMATE GAS COST (FOR LOGS ONLY) ===
    let gasCostUsd = 0;
    try {
      const contract = new web3.eth.Contract(ArtistSharesTokenABI, contractAddress);
      const gasEstimate = await contract.methods.buy(minTokensOutWei.toString()).estimateGas({
        from: userAddress,
        value: ethInWei.toString(),
      });
      const gasPrice = await web3.eth.getGasPrice();
      const gasCostEth = Number(gasEstimate) * Number(gasPrice) / 1e18;
      gasCostUsd = gasCostEth * ethUsd;
      log("Estimated gas cost: $", gasCostUsd.toFixed(2));
    } catch (e) {
      log("Gas estimation failed:", e.message);
    }

    // 6. Capping – 5 % of total supply & remaining curve tokens
    const totalSupplyWei = BigInt(1_000_000_000) * 1_000_000_000_000_000_000n;
    const maxHoldingWei = totalSupplyWei / 20n;
    const maxTokensWei = maxHoldingWei - userBalanceWei;

    let capped = false;
    if (tokensOutWei > tokensInCurveWei) {
      tokensOutWei = tokensInCurveWei;
      capped = true;
    }
    if (tokensOutWei > maxTokensWei && maxTokensWei > 0n) {
      tokensOutWei = maxTokensWei;
      capped = true;
    }

    // If we were capped, recompute the exact ETH needed for the capped amount
    if (capped) {
      const ethInCapped = await contract.methods
        .getEthNeededForBuy(tokensOutWei.toString())
        .call();
      ethInWei = ethInCapped;
    }

    // 7. Daily-trade-limit check (read limit from the contract)
    let dailyTradeVolumeUsd = 0;
    try {
      const res = await axios.get(
        `${import.meta.env.VITE_API_URL}/api/blockchain/financials/by-user/${userAddress}`,
        { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
      );
      dailyTradeVolumeUsd = Number(res.data) || 0;
      log("Fetched daily volume USD:", dailyTradeVolumeUsd);
    } catch (err) {
      log("Daily volume fetch failed – fallback to 0:", err.message);
      dailyTradeVolumeUsd = 0;
    }

    const ethInEth = Number(ethAfterFee);                     // ETH after fee
    const totalCostUsd = ethInEth * ethPriceUsd;              // $ spent

    const dailySellLimitUsdRaw = await contract.methods.dailySellLimitUsd().call();
    const dailySellLimitUsd = Number(dailySellLimitUsdRaw) / 1e8;   // micro-cents → USD

    log("DAILY LIMIT CHECK:", {
      ethInWei: ethInWei.toString(),
      ethInEth: ethInEth.toFixed(18),
      ethUsd: ethPriceUsd,
      totalCostUsd: totalCostUsd.toFixed(6),
      dailyTradeVolumeUsd: dailyTradeVolumeUsd.toFixed(2),
      total: (dailyTradeVolumeUsd + totalCostUsd).toFixed(2),
      limit: dailySellLimitUsd
    });

    if (dailyTradeVolumeUsd + totalCostUsd > dailySellLimitUsd) {
      throw new Error(
        `Exceeds daily trade limit (used $${dailyTradeVolumeUsd.toFixed(2)}, trying $${totalCostUsd.toFixed(2)})`
      );
    }

    // 8. Final price in Wei (for UI)
    const priceWei = ethUsd > 0
      ? BigInt(Math.floor(priceUsd * 1e18 / ethUsd))
      : 0n;

    log("BUY DEBUG:", {
      dollarAmount,
      priceUsd: priceUsd.toFixed(8),
      totalCostUsd: totalCostUsd.toFixed(6),
      tokensOutWei: tokensOutWei.toString(),
      minTokensOutWei: minTokensOutWei.toString(),
    });

    return {
      success: true,
      amount: tokensOutWei.toString(),
      totalCost: ethInWei.toString(),
      minTokensOut: minTokensOutWei.toString(),
      price: priceWei.toString()
    };

  } catch (error) {
    console.error("Buy pre-check failed:", error);
    return { success: false, error: error?.message || error.toString() };
  }
}