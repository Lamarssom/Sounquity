import React, { useEffect, useRef, useState } from "react";
import { getWeb3 } from "../utilities/web3";
import ArtistSharesTokenArtifact from "../abis/ArtistSharesTokenABI.json";
import { fetchEthPrice } from "../utilities/fetchEthPrice";
import logger from "../utilities/logger"; // Import logger
import "../styles/LiveTransactions.css";

const abi = ArtistSharesTokenArtifact.abi;

const LiveTransactions = ({ contractAddress }) => {
  const [trades, setTrades] = useState([]);
  const lastBlockRef = useRef(null);
  const pollingInterval = 30000; // 30 seconds

  useEffect(() => {
    if (!contractAddress) {
    //  logger.error("LiveTransactions", "Missing contractAddress");
      return;
    }

    const web3 = getWeb3();
    const contract = new web3.eth.Contract(abi, contractAddress);

    logger.info("LiveTransactions", "ABI Events:", abi.filter((i) => i.type === "event").map((e) => e.name));
    // logger.info("LiveTransactions", "Watching contract:", contractAddress);

    let poller;
    let isPolling = process.env.NODE_ENV === "development";

    window.togglePolling = () => {
      isPolling = !isPolling;
      logger.info("LiveTransactions", `Polling ${isPolling ? "enabled" : "disabled"}`);
    };

    const fetchNewEvents = async () => {
      if (!isPolling) return;
      try {
        let latestBlock = await web3.eth.getBlockNumber();
        if (typeof latestBlock === "bigint") latestBlock = Number(latestBlock);
        let fromBlock = lastBlockRef.current;
        if (typeof fromBlock === "bigint") fromBlock = Number(fromBlock);
        if (!fromBlock) fromBlock = Math.max(latestBlock - 50, 0);

        if (fromBlock >= latestBlock) {
          logger.debug("LiveTransactions", `Polling: No new blocks (from ${fromBlock} to ${latestBlock})`);
          return;
        }

        logger.debug("LiveTransactions", `Polling: Checking blocks ${fromBlock} → ${latestBlock}`);

        const ethToUsdRate = await fetchEthPrice(web3, contractAddress);
        if (!ethToUsdRate || ethToUsdRate <= 0) {
        //  logger.warn("LiveTransactions", "Invalid ETH/USD rate:", ethToUsdRate);
          return;
        }

        const [buyEvents, sellEvents] = await Promise.all([
          contract.getPastEvents("SharesBought", { fromBlock, toBlock: "latest" }),
          contract.getPastEvents("SharesSold", { fromBlock, toBlock: "latest" }),
        ]);

        const allEvents = [...buyEvents, ...sellEvents].sort((a, b) => {
          const blockA = BigInt(a.blockNumber);
          const blockB = BigInt(b.blockNumber);
          return blockA < blockB ? 1 : blockA > blockB ? -1 : 0;
        });

        const formatted = allEvents.map((event) => {
          const isBuy = event.event === "SharesBought";
          const { buyer, seller, amount, price, timestamp } = event.returnValues;
          const user = isBuy ? buyer : seller;

          // Handle amount (shares) and price (wei)
          const amountStr = amount ? amount.toString() : "0";
          const priceStr = price ? price.toString() : "0";
          let amountInEth, priceInEth, amountInUsd, priceInUsd;

          try {
            amountInEth = parseFloat(amountStr) || 0; // amount is share count
            priceInEth = parseFloat(web3.utils.fromWei(priceStr, "ether")) || 0;
            amountInUsd = amountInEth * priceInEth * ethToUsdRate * (isBuy ? 0.98 : 1);
            priceInUsd = priceInEth * ethToUsdRate;
          } catch (error) {
            logger.warn("LiveTransactions", "Calculation error:", error.message, { amountStr, priceStr });
            amountInEth = 0;
            priceInEth = 0;
            amountInUsd = 0;
            priceInUsd = 0;
          }

          return {
            type: isBuy ? "BUY" : "SELL",
            user,
            amountInUsd,
            priceInUsd,
            timestamp: new Date(parseInt(timestamp) * 1000),
            txHash: event.transactionHash,
          };
        });

        if (formatted.length > 0) {
          setTrades((prev) => {
            const seen = new Set(prev.map((t) => t.txHash));
            const newOnes = formatted.filter((t) => !seen.has(t.txHash));
            return [...newOnes, ...prev].slice(0, 20);
          });
        }

        lastBlockRef.current = latestBlock;
      } catch (err) {
        logger.error("LiveTransactions", "Polling error:", err.message);
      }
    };

    fetchNewEvents();
    poller = setInterval(fetchNewEvents, pollingInterval);

    return () => {
      clearInterval(poller);
      delete window.togglePolling;
    };
  }, [contractAddress]);

  const sortedTrades = [...trades].sort((a, b) => b.timestamp - a.timestamp);

  return (
    <div className="live-transactions">
      <h3>Live Trades</h3>
      <div className="trade-table-container">
        {trades.length === 0 ? (
          <p className="no-trades">No trades yet.</p>
        ) : (
          <table className="trade-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>User</th>
                <th>Amount ($)</th>
                <th>Price ($)</th>
                <th>Time</th>
                <th>TX Hash</th>
              </tr>
            </thead>
            <tbody>
              {sortedTrades.map((t, i) => (
                <tr key={i} className={i === 0 ? "recent-trade" : ""}>
                  <td style={{ color: t.type === "BUY" ? "#26A69A" : "#EF5350" }}>
                    {t.type} <span className="arrow">{i === 0 ? (t.type === "BUY" ? "↑" : "↓") : ""}</span>
                  </td>
                  <td>
                    <a href={`https://etherscan.io/address/${t.user}`} target="_blank" rel="noopener noreferrer">
                      {t.user.slice(0, 6)}...{t.user.slice(-4)}
                    </a>
                  </td>
                  <td>${t.amountInUsd.toFixed(2)}</td>
                  <td>${t.priceInUsd.toFixed(6)}</td>
                  <td>{getRelativeTime(t.timestamp)}</td>
                  <td>
                    <a href={`https://etherscan.io/address/${t.user}`} target="_blank" rel="noopener noreferrer">
                      {t.txHash.slice(0, 10)}...
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

const getRelativeTime = (date) => {
  const now = new Date();
  const diff = Math.floor((now - date) / 1000);
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return date.toLocaleTimeString();
};

export default LiveTransactions;