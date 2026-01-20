import React, { useState, useEffect, useRef, useMemo } from "react";
import { useParams, useLocation } from "react-router-dom";
import "../styles/ArtistDetails.css";
import ChatBox from "../components/ChatBox";
import ArtistChart from "../components/ArtistChart";
import SpotifyService from "../services/SpotifyService";
import { getHttpWeb3, getWalletWeb3 } from "../utilities/web3";
import { getFactoryContract } from "../utilities/getFactoryContract";
import { safeBuyHandler } from "../handlers/safeBuyHandler";
import { safeSellHandler } from "../handlers/safeSellHandler";
import LiveTransactions from "../components/LiveTransactions";
import { Container, Row, Col, Card, Button, Form, Alert, Spinner, ProgressBar, OverlayTrigger, Tooltip } from "react-bootstrap";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import axios from "axios";
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useQueryClient } from "@tanstack/react-query";
import { useArtistFinancials } from "../hooks/useArtistFinancials";
import logger from "../utilities/logger";
import rawAbi from "../abis/ArtistSharesTokenABI.json";
const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

const generateSymbol = (name) => {
  if (!name) return "ARTIST";
  const clean = name.replace(/[^a-zA-Z]/g, "").toUpperCase();
  return clean.length >= 3 ? clean.substring(0, 7) : clean;
};

const ArtistDetails = () => {
  const { contractAddress } = useParams();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [artistDetails, setArtistDetails] = useState(null);
  const [spotifyData, setSpotifyData] = useState(null);
  const [walletWeb3, setWalletWeb3] = useState(null);
  const [httpWeb3, setHttpWeb3] = useState(null);
  const [account, setAccount] = useState(null);
  const [loading, setLoading] = useState(true);
  const [resolvedAddress, setResolvedAddress] = useState("");
  const [metaMaskAvailable, setMetaMaskAvailable] = useState(true);
  const [readyToTrade, setReadyToTrade] = useState(false);
  const [buying, setBuying] = useState(false);
  const [selling, setSelling] = useState(false);
  const [chartRefreshKey, setChartRefreshKey] = useState(0);
  const [dollarAmount, setDollarAmount] = useState("");
  const [slippage, setSlippage] = useState("2"); // Default 2% slippage
  const [estimatedShares, setEstimatedShares] = useState(null);
  const chartRef = useRef();
  const [cooldownRemaining, setCooldownRemaining] = useState(0);

  const isDev = process.env.NODE_ENV === "development";
  const log = (prefix, ...args) => isDev && logger.debug(prefix, ...args);

  const [artistId, setArtistId] = useState(() => {
    return location.state?.artistId || 
          location.state?.id ||         
          location.state?.spotifyId || 
          null;
  });

  console.log('[DEBUG ArtistDetails] Reactive artistId:', artistId);

  const { financials, isLoading: financialsLoading, error: financialsError } = useArtistFinancials(artistId);

  useEffect(() => {
    if (!artistId) {
      console.log('[DEBUG WebSocket] No artistId → skipping subscription');
      return;
    }

    console.log('[DEBUG WebSocket] Subscribing to /topic/financials/' + artistId);

    const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    const wsUrl = (apiUrl.replace('https', 'wss').replace('/api', '') + '/ws').replace('http', 'ws');
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
    });

    client.onConnect = () => {
      console.log('[DEBUG WebSocket] Connected! Subscribing...');
      client.subscribe(`/topic/financials/${artistId}`, (msg) => {
        console.log('[DEBUG WebSocket] MESSAGE RECEIVED:', msg.body);
        try {
          const data = JSON.parse(msg.body);
          queryClient.setQueryData(['financials', artistId], data);
          queryClient.invalidateQueries({ queryKey: ['financials', artistId] });
        } catch (e) {
          console.error('Parse error:', e);
        }
      });
    };

    client.activate();

    return () => {
      console.log('[DEBUG WebSocket] Deactivating for', artistId);
      client.deactivate();
    };
  }, [artistId, queryClient]);

  useEffect(() => {
    if (!dollarAmount || !httpWeb3 || !resolvedAddress) {
      setEstimatedShares(null);
      return;
    }
    let canceled = false;

    const estimate = async () => {
      try {
        const contract = new httpWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
        const [priceMicroRaw, ethUsdRaw, feeRaw, tokensInCurveRaw] = await Promise.all([
          contract.methods.getCurrentPriceMicroUSD().call(),
          contract.methods.getEthUsdPrice().call(),
          contract.methods.BUY_FEE().call(),
          contract.methods.tokensInCurve().call()
        ]);

        const priceMicro = Number(priceMicroRaw);
        const ethUsd = Number(ethUsdRaw) / 1e8;
        const fee = Number(feeRaw);
        const tokensInCurve = Number(tokensInCurveRaw) / 1e18;


        let priceUsd = priceMicro / 1e8;
        // Fallback for zero price
        if (priceUsd === 0 && tokensInCurve > 0) {
          const TARGET_FDV_USD = 1000;
          const TOTAL_SUPPLY = 1_000_000_000;
          const progress = tokensInCurve / TOTAL_SUPPLY;
          priceUsd = progress * TARGET_FDV_USD / TOTAL_SUPPLY;
        }

        if (priceUsd <= 0 || ethUsd <= 0) {
          setEstimatedShares(null);
          return;
        }

        const usd = parseFloat(dollarAmount);
        const ethBefore = usd / ethUsd;
        const ethAfter = ethBefore * 10000 / (10000 - fee);
        const ethWei = httpWeb3.utils.toWei(ethAfter.toFixed(18), 'ether');

        const tokensRaw = await contract.methods.getTokensForEth(ethWei).call();
        const tokens = parseFloat(httpWeb3.utils.fromWei(tokensRaw, 'ether'));

        if (!canceled) setEstimatedShares(tokens);
      } catch (err) {
        console.error("Estimate failed:", err);

        // ---- FALLBACK using curve progress (matches contract fallback) ----
        if (dollarAmount && tokensInCurve > 0) {
          const usd = parseFloat(dollarAmount);
          const TARGET_FDV_USD = 1_000;               // $1k target (same as contract)
          const TOTAL_SUPPLY   = 1_000_000_000;
          const progress = tokensInCurve / TOTAL_SUPPLY;
          const priceUsd = (progress * TARGET_FDV_USD) / TOTAL_SUPPLY;
          const tokens   = usd / priceUsd;
          if (!canceled) setEstimatedShares(tokens);
          return;
        }

        if (!canceled) setEstimatedShares(null);
      }
    };

    const id = setTimeout(estimate, 80);
    return () => {
      canceled = true;
      clearTimeout(id);
    };
  }, [dollarAmount, httpWeb3, resolvedAddress]);

  useEffect(() => {
    if (financials) {
      log("[ArtistDetails] Financials loaded:", financials);
    }
    if (financialsError) {
      log("[ArtistDetails] Financials error:", financialsError);
    }
  }, [financials, financialsError]);

  useEffect(() => {
    const fetchFinancialsFallback = async () => {
      if (
        financials.currentPrice === "N/A" &&
        financials.volume24h === "N/A" &&
        financials.marketCap === "N/A" &&
        artistId
      ) {
        log("[ArtistDetails] Financials are N/A, attempting fallback fetch for artistId:", artistId);
        try {
          const response = await axios.get(
            `${import.meta.env.VITE_API_URL}/blockchain/financials/${artistId}`,
            { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
          );
          log("[ArtistDetails] Fallback financials fetched:", response.data);
          queryClient.setQueryData(['financials', artistId], response.data);
        } catch (err) {
          log("[ArtistDetails] Fallback financials fetch failed:", err.message);
          toast.error("Failed to load financial data.");
        }
      }
    };

    fetchFinancialsFallback();
  }, [artistId, financials, queryClient]);

  const contract = useMemo(() => {
    if (httpWeb3 && resolvedAddress) {
      return new httpWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
    }
    return null;
  }, [httpWeb3, resolvedAddress]);

  const memoizedArtistDetails = useMemo(
    () => ({
      artistId: artistDetails?.artistId || "",
      artistName: artistDetails?.artistName || "Unknown",
      imageUrl: artistDetails?.imageUrl || "",
      spotifyUrl: artistDetails?.spotifyUrl || "",
      popularity: artistDetails?.popularity || 0,
      price: financials.currentPrice !== "N/A" ? financials.currentPrice : "N/A",
      volume: financials.volume24h !== "N/A" ? financials.volume24h : "N/A",
      marketCap: financials.marketCap !== "N/A" ? financials.marketCap : "N/A",
      dailyLiquidity: financials.dailyLiquidity || 0,
      liquidityPercentage: financials.liquidityPercentage || 100,
      availableSupply: financials.availableSupply || 0,
      nextReset: financials.nextReset ? new Date(financials.nextReset) : null,
    }),
    [artistDetails, financials]
  );

  const isContractAddress = (val) => val?.startsWith("0x") && val.length === 42;

  const triggerChartRefresh = () => {
    setChartRefreshKey((prev) => prev + 1);
  };

  const formatVolume = (value, isShares = false) => {
    try {
      const parsed = typeof value === "number" ? value : parseFloat(value.toString());
      if (isNaN(parsed)) return "0";
      if (isShares) {
        if (parsed >= 1_000_000_000) return `${(parsed / 1_000_000_000).toFixed(2)}B`;
        if (parsed >= 1_000_000) return `${(parsed / 1_000_000).toFixed(2)}M`;
        if (parsed >= 1_000) return `${(parsed / 1_000).toFixed(2)}K`;
        return parsed.toFixed(2);
      }
      if (parsed >= 1_000_000_000_000) return `$${(parsed / 1_000_000_000_000).toFixed(2)}T`;
      if (parsed >= 1_000_000_000) return `$${(parsed / 1_000_000_000).toFixed(2)}B`;
      if (parsed >= 1_000_000) return `$${(parsed / 1_000_000).toFixed(2)}M`;
      if (parsed >= 1_000) return `$${(parsed / 1_000).toFixed(2)}K`;
      return `$${parsed.toFixed(2)}`;
    } catch (err) {
      console.error("formatVolume error:", err);
      return "0";
    }
  };

  const formatMarketCap = (value) => {
    try {
      if (typeof value === "string" && value !== "N/A") {
        return value;
      }
      const parsed = typeof value === "number" ? value : parseFloat(value.toString());
      if (isNaN(parsed) || parsed === null) return "N/A";
      if (parsed >= 1_000_000_000_000) return `$${(parsed / 1_000_000_000_000).toFixed(2)}T`;
      if (parsed >= 1_000_000_000) return `$${(parsed / 1_000_000_000).toFixed(2)}B`;
      if (parsed >= 1_000_000) return `$${(parsed / 1_000_000).toFixed(2)}M`;
      if (parsed >= 1_000) return `$${(parsed / 1_000).toFixed(2)}K`;
      return `$${parsed.toFixed(2)}`;
    } catch (err) {
      console.error("formatMarketCap error:", err);
      return "N/A";
    }
  };

  useEffect(() => {
    const initWeb3 = async () => {
      if (!window.ethereum) {
        setMetaMaskAvailable(false);
        console.log("[Wallet] No MetaMask detected — view-only mode");
        // Do NOT block — continue with httpWeb3 only
        setHttpWeb3(getHttpWeb3()); // Ensure public web3 is always set
        setLoading(false);
        return;
      }

      try {
        // Comment out auto-request to avoid prompt on view-only
        // const accounts = await window.ethereum.request({ method: "eth_requestAccounts" });

        const injectedWeb3 = getWalletWeb3();
        const readOnlyWeb3 = getHttpWeb3();

        setWalletWeb3(injectedWeb3);
        setHttpWeb3(readOnlyWeb3);
        // setAccount(accounts[0]);  ← only set if connected

        const handleAccountsChanged = (newAccounts) => {
          if (newAccounts.length > 0) {
            setAccount(newAccounts[0]);
            toast.info("Wallet connected!");
          } else {
            setAccount(null);
            toast.info("Wallet disconnected.");
          }
        };

        window.ethereum.on("accountsChanged", handleAccountsChanged);
        window.ethereum.on("chainChanged", () => window.location.reload());

        return () => {
          window.ethereum.removeListener("accountsChanged", handleAccountsChanged);
        };
      } catch (err) {
        console.error("Wallet init failed:", err);
        setMetaMaskAvailable(false);
      } finally {
        setLoading(false);
      }
    };

    initWeb3();
  }, []);

  const updateContractAddress = async (artistId, contractAddress) => {
    try {
      await axios.put(
        `${import.meta.env.VITE_API_URL}/artists/${artistId}/update-contract`,
        { contractAddress },
        { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
      );
      log("[CONTRACT] Backend updated with new contract address:", contractAddress);
    } catch (err) {
      console.error("[CONTRACT] Failed to update contract address:", err);
      toast.error("Failed to update artist contract in database.");
    }
  };

  useEffect(() => {
    const loadArtist = async () => {
      if (!artistId) {
        toast.error("No artist ID provided");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);

        // 1. Try to get enriched data from our DB (image, etc.)
        let artistData = null;
        try {
          const res = await axios.get(`${import.meta.env.VITE_API_URL}/artists/${artistId}`);
          artistData = res.data;
        } catch (e) {
          console.warn("Artist not in our DB yet, falling back to Spotify");
        }

        // 2. Always fetch fresh Spotify data
        const spotifyData = await SpotifyService.getArtistDetailsFromSpotify(artistId);

        setArtistDetails({
          ...spotifyData,
          ...artistData, // override with DB data if exists
          contractAddress: resolvedAddress || contractAddress,
        });
        setSpotifyData(spotifyData);

      } catch (err) {
        console.error("Failed to load artist:", err);
        toast.error("Failed to load artist details");
      } finally {
        setLoading(false);
      }
    };

    loadArtist();
  }, [artistId, resolvedAddress, contractAddress]);

  // ADD THIS ENTIRE BLOCK — THIS IS THE MISSING PIECE
  useEffect(() => {
    if (contractAddress && contractAddress.startsWith("0x") && contractAddress.length === 42) {
      log("[ArtistDetails] Valid contract address from URL:", contractAddress);
      setResolvedAddress(contractAddress);
    } else if (artistDetails?.contractAddress) {
      log("[ArtistDetails] Using contract address from artist data:", artistDetails.contractAddress);
      setResolvedAddress(artistDetails.contractAddress);
    } else {
      log("[ArtistDetails] No valid contract address found yet");
      setResolvedAddress("");
    }
  }, [contractAddress, artistDetails]);

  useEffect(() => {
    console.log("[readyToTrade DEBUG]", {
      loading,
      financialsLoading,
      financialsError,
      hasArtistDetails: !!artistDetails,
      hasResolvedAddress: !!resolvedAddress,
      hasAccount: !!account,
      hasWalletWeb3: !!walletWeb3,
      hasHttpWeb3: !!httpWeb3,
    });

    const canTrade = (
      !loading &&
      !financialsError &&          
      artistDetails &&
      resolvedAddress &&
      walletWeb3 &&
      httpWeb3
    );
    setReadyToTrade(canTrade);
  }, [loading, financialsLoading, financialsError, artistDetails, resolvedAddress, account, walletWeb3, httpWeb3]);

  useEffect(() => {
    if (!account || !contract) return;

    const checkCooldown = async () => {
      try {
        const lastSell = await contract.methods.lastSellTime(account).call();
        const cooldownEnd = Number(lastSell) + 3600; // 1 hour
        const now = Math.floor(Date.now() / 1000);
        const remaining = cooldownEnd - now;
        setCooldownRemaining(remaining > 0 ? remaining : 0);
      } catch {
        setCooldownRemaining(0);
      }
    };

    checkCooldown();
    const interval = setInterval(checkCooldown, 10000);
    return () => clearInterval(interval);
  }, [account, contract]);

  const onBuySuccess = async (tokensBought) => {
    await queryClient.invalidateQueries({ queryKey: ['financials', artistId] });
    toast.success(`Bought ${tokensBought.toFixed(2)} shares!`);
    triggerChartRefresh();
  };

  const handleBuy = async () => {
    if (buying) return;

    // NEW: Early checks with toast (no wallet/account)
    if (!metaMaskAvailable) {
      toast.error("MetaMask (or compatible wallet) not detected. Install one to trade.");
      return;
    }

    if (!account) {
      toast.info("Connect your wallet to buy shares.");
      return;
    }

    if (!readyToTrade || !walletWeb3 || !resolvedAddress) {
      toast.error("Trading not ready yet. Try again in a moment.");
      return;
    }

    const dollarAmountNum = parseFloat(dollarAmount);
    const slippageNum = parseFloat(slippage);
    if (isNaN(dollarAmountNum) || dollarAmountNum <= 0) return toast.error("Enter a valid amount");
    if (isNaN(slippageNum) || slippageNum < 0 || slippageNum > 100) return toast.error("Invalid slippage");

    setBuying(true);
    toast.info("Estimating buy...");

    try {
      const result = await safeBuyHandler(walletWeb3, resolvedAddress, account, dollarAmountNum, slippageNum);
      if (!result.success) throw new Error(result.error);

      const contract = new walletWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
      const totalCostWei = result.totalCost;
      const minTokensOut = result.minTokensOut;

      let gasLimit = 700_000;
      try {
        const estimated = await contract.methods.buy(minTokensOut).estimateGas({
          from: account,
          value: totalCostWei.toString(),
        });
        gasLimit = Math.floor(estimated * 1.5);
      } catch (e) { /* ignore */ }

      const tx = await contract.methods.buy(minTokensOut).send({
        from: account,
        value: result.totalCost,
        gas: gasLimit,
      });

      const tokensBought = Number(result.amount) / 1e18;
      await onBuySuccess(tokensBought);
      triggerChartRefresh();
    } catch (err) {
      console.error(err);
      toast.error(err.message || "Buy failed");
    } finally {
      setBuying(false);
    }
  };

  const onSellSuccess = async (tokensSold) => {
    await queryClient.invalidateQueries({ queryKey: ['financials', artistId] });
    toast.success(`Sold ${tokensSold.toLocaleString(undefined, { maximumFractionDigits: 2 })} shares!`);
    triggerChartRefresh();
  };

  
  const handleSell = async () => {
    if (selling) return;

    // NEW: Same early toast checks as buy
    if (!metaMaskAvailable) {
      toast.error("MetaMask not detected. Install one to trade.");
      return;
    }

    if (!account) {
      toast.info("Connect your wallet to sell shares.");
      return;
    }

    if (!readyToTrade || !walletWeb3 || !resolvedAddress) {
      toast.error("Trading not ready. Try again.");
      return;
    }

    if (!walletWeb3.utils.isAddress(resolvedAddress)) {
      toast.error("Invalid contract address.");
      return;
    }

    const dollarAmountNum = parseFloat(dollarAmount);
    const slippageNum = parseFloat(slippage);
    if (isNaN(dollarAmountNum) || dollarAmountNum <= 0) return toast.error("Enter a valid amount");
    if (isNaN(slippageNum) || slippageNum < 0 || slippageNum > 100) return toast.error("Invalid slippage");

    setSelling(true);
    toast.info("Processing sell...");

    try {
      const result = await safeSellHandler(walletWeb3, resolvedAddress, account, dollarAmountNum, slippageNum);
      if (!result.success) {
        toast.error(result.error);
        return;
      }

      const contract = new walletWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
      let estimatedGas, safeGasLimit;
      try {
        estimatedGas = await contract.methods.sell(result.amount, result.minEthOut).estimateGas({ from: account });
        safeGasLimit = Math.max(Math.floor(Number(estimatedGas) * 1.5), 700_000);
      } catch (err) {
        safeGasLimit = 700_000;
      }

      const tx = await contract.methods.sell(result.amount, result.minEthOut).send({
        from: account,
        gas: safeGasLimit,
      });

      const tokensSold = Number(result.amount) / 1e18;
      await onSellSuccess(tokensSold);
    } catch (err) {
      console.error("[SELL] Error:", err);
      let message = "Sell failed";

      if (err.message.includes("Cooldown")) message = "1-hour cooldown between sells";
      else if (err.message.includes("Daily token cap")) message = "5% total supply limit per day";
      else if (err.message.includes("Exceeds daily USD limit")) message = "$50,000 daily sell limit reached";
      else if (err.message.includes("Not enough ETH")) message = "Not enough liquidity in curve";

      toast.error(message);
    } finally {
      setSelling(false);
    }
  };

  if (!loading && !artistDetails) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <h3>Artist not found or data unavailable</h3>
      </div>
    );
  }

  if (loading || financialsLoading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading artist details...</p>
      </div>
    );
  }

  if (financialsError) {
    toast.error("Failed to load financial data.");
  }

  return (
    <Container fluid className="artist-details p-0">
      <ToastContainer position="top-right" autoClose={3000} hideProgressBar />
      <Row className="g-3 mx-0">
        <Col md={8} className="px-2">
          <Card className="h-100 card-glass">
            <Card.Body className="chart-card-body p-0 d-flex flex-column">
              <div className="chart-inner-wrapper h-100 d-flex flex-column">
                {artistDetails.contractAddress ? (
                  <div className="chart-stretch-wrapper h-100 d-flex flex-column">
                    <ArtistChart
                      financials={financials}
                      ref={chartRef}
                      contractAddress={resolvedAddress || contractAddress}
                      artistId={artistDetails.artistId}
                      refreshTrigger={chartRefreshKey}
                    />
                  </div>
                ) : (
                  <p>Loading chart...</p>
                )}
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={4} className="px-2">
          <Card className="card-glass artist-info-card">
            <Card.Body className="artist-info-body">
              <div className="artist-info-container">
                <div className="artist-image">
                  <img
                    src={artistDetails.imageUrl || "/default-artist.jpg"}
                    onError={(e) => (e.target.src = "/default-artist.jpg")}
                    alt="Artist"
                    className="rounded-circle object-fit-cover"
                  />
                </div>
                <div className="artist-metrics">
                  <div className="artist-header-clean mb-4">
                  {/* Artist Name */}
                  <h4 className="artist-name-yellow mb-2">
                    {artistDetails.artistName || "Loading..."}
                  </h4>

                  {/* ONLY: $SYMBOL + Copy Icon — no text, no clutter */}
                  <div className="symbol-copy-row">
                    <span className="artist-symbol-final">
                      ${artistDetails.symbol || generateSymbol(artistDetails.artistName || "ARTIST")}
                    </span>

                    {/* Bright, always-visible, golden copy icon */}
                    <OverlayTrigger placement="top" overlay={<Tooltip>Copy contract address</Tooltip>}>
                      <button
                        className="btn-copy-golden"
                        onClick={() => {
                          navigator.clipboard.writeText(resolvedAddress || contractAddress);
                          toast.success("Contract address copied!");
                        }}
                      >
                        <i className="bi bi-clipboard"></i>
                      </button>
                    </OverlayTrigger>
                  </div>
                </div>
                  <Row className="g-2">
                    <Col xs={12} sm={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Current price per share</Tooltip>}
                      >
                        <p className="mb-1">Price: {memoizedArtistDetails.price}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Total value of all outstanding shares</Tooltip>}
                      >
                        <p className="mb-1">Market Cap: {memoizedArtistDetails.marketCap}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Total value of tokens available for trading today (USD)</Tooltip>}
                      >
                        <p className="mb-1">Daily Liquidity: {memoizedArtistDetails.dailyLiquidity ? formatMarketCap(memoizedArtistDetails.dailyLiquidity) : "N/A"}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Real ETH liquidity currently in the bonding curve (sellable)</Tooltip>}
                      >
                        <p className="mb-1">
                          Liquidity in Curve: ${financials.ethLiquidityInCurveUsd?.toFixed(0) || "0"}k
                        </p>
                      </OverlayTrigger>
                    </Col>
                    <Col xs={12} sm={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Trading volume in the last 24 hours</Tooltip>}
                      >
                        <p className="mb-1">24h Volume: {memoizedArtistDetails.volume}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Shares available for trading</Tooltip>}
                      >
                        <p className="mb-1">Tradable Shares: {memoizedArtistDetails.availableSupply ? formatVolume(memoizedArtistDetails.availableSupply, true) : "N/A"}</p>
                      </OverlayTrigger>
                    </Col>
                    <Col xs={12}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Progress to Uniswap listing ($69k FDV)</Tooltip>}
                      >
                        <div>
                          <p className="mb-1 fw-bold">Bonding Curve Progress</p>
                          <ProgressBar
                            now={financials.liquidityPercentage || 0}
                            label={`${(financials.liquidityPercentage || 0).toFixed(1)}% → Uniswap`}
                            variant={
                              financials.liquidityPercentage >= 98 ? "success" :
                              financials.liquidityPercentage >= 80 ? "warning" : "info"
                            }
                            className="mb-2"
                            style={{ height: "24px" }}
                          />
                        </div>
                      </OverlayTrigger>
                    </Col>
                    <Col xs={12} sm={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Artist's Spotify followers</Tooltip>}
                      >
                        <p className="mb-1">Followers: {spotifyData?.followers ? formatVolume(spotifyData.followers, true) : "N/A"}</p>
                      </OverlayTrigger>
                    </Col>
                    <Col xs={12} sm={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Artist's Spotify popularity score</Tooltip>}
                      >
                        <p className="mb-1">Popularity: {spotifyData?.popularity ?? "N/A"}</p>
                      </OverlayTrigger>
                    </Col>
                  </Row>
                  {artistDetails.spotifyUrl && (
                    <a
                      href={artistDetails.spotifyUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="spotify-link"
                    >
                      Listen on Spotify
                    </a>
                  )}
                </div>
              </div>
              <div className="trade-controls mt-2">
                <Form.Group className="mb-2">
                  <Form.Label htmlFor="dollar-amount">Amount (USD)</Form.Label>
                  <Form.Control
                    id="dollar-amount"
                    type="number"
                    value={dollarAmount}
                    onChange={(e) => setDollarAmount(e.target.value)}
                    placeholder="Enter $ amount"
                    min="0"
                    step="0.01"
                    aria-describedby="share-estimate"
                    className="form-control"
                  />
                  {estimatedShares !== null ? (
                    <div className="estimated-shares">
                      ~{estimatedShares.toFixed(2)} shares
                    </div>
                  ) : dollarAmount && memoizedArtistDetails.price !== "N/A" ? (
                    <div className="estimated-shares loading">
                      Estimating...
                    </div>
                  ) : null}
                </Form.Group>

                <Form.Group className="mb-2">
                  <Form.Label htmlFor="slippage">Slippage Tolerance (%)</Form.Label>
                  <Form.Control
                    id="slippage"
                    type="number"
                    value={slippage}
                    onChange={(e) => setSlippage(e.target.value)}
                    placeholder="Enter slippage %"
                    min="0"
                    max="100"
                    step="0.1"
                    className="form-control"
                  />
                </Form.Group>

                <div className="trade-buttons d-flex flex-wrap gap-2">
                  {/* Your existing quick amount buttons */}
                  <Button
                    variant="outline-light"
                    onClick={() => setDollarAmount((prev) => (prev ? (parseFloat(prev) + 50) * 1 : 50).toString())}
                    className="trade-btn"
                  >
                    $50
                  </Button>
                  {/* ... $100, $500, $1000 buttons ... */}

                  <Button
                    className="btn-gradient trade-btn"
                    onClick={handleBuy}
                    disabled={!readyToTrade || buying || financials.currentPrice === "N/A" || !resolvedAddress}
                    aria-label="Buy shares"
                  >
                    {buying ? <Spinner size="sm" animation="border" /> : "Buy"}
                  </Button>

                  <Button
                    variant={cooldownRemaining > 0 ? "secondary" : "danger"}
                    onClick={handleSell}
                    disabled={!readyToTrade || selling || cooldownRemaining > 0 || !resolvedAddress}
                    className="trade-btn"
                    aria-label="Sell shares"
                  >
                    {selling ? (
                      <Spinner size="sm" animation="border" />
                    ) : cooldownRemaining > 0 ? (
                      `Sell Cooldown: ${Math.floor(cooldownRemaining / 60)}m ${cooldownRemaining % 60}s`
                    ) : (
                      "Sell"
                    )}
                  </Button>
                </div>
              </div> 
            </Card.Body>
          </Card>
        </Col>
      </Row>

      <Row className="g-3 mx-0 mt-3">
        <Col md={8} className="px-2">
          <Card className="card-glass">
            <Card.Body className="transactions-card-body">
              <LiveTransactions
                contractAddress={resolvedAddress || contractAddress}
                walletWeb3={walletWeb3}
                httpWeb3={httpWeb3}
              />
            </Card.Body>
          </Card>
        </Col>
        <Col md={4} className="px-2">
          <Card className="card-glass">
            <Card.Body>
              <div className="chatbox">
                {(() => {
                  const userId = account || "0xAnonymous";
                  console.log("[ArtistDetails] Passing userId to ChatBox:", userId);
                  return <ChatBox contractAddress={resolvedAddress || contractAddress} userId={userId} />;
                })()}
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
};

export default ArtistDetails;