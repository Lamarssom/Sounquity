import React, { useState, useEffect, useRef, useMemo } from "react";
import { useParams, useLocation } from "react-router-dom";
import "../styles/ArtistDetails.css";
import ChatBox from "../components/ChatBox";
import ArtistChart from "../components/ArtistChart";
import { updateArtistContractAddress } from "../services/api";
import SpotifyService from "../services/SpotifyService";
import { getHttpWeb3, getWeb3 } from "../utilities/web3";
import { getFactoryContract } from "../utilities/getFactoryContract";
import { safeBuyHandler } from "../handlers/safeBuyHandler";
import { safeSellHandler } from "../handlers/safeSellHandler";
import LiveTransactions from "../components/LiveTransactions";
import { Container, Row, Col, Card, Button, Form, Alert, Spinner, ProgressBar, OverlayTrigger, Tooltip } from "react-bootstrap";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import axios from "axios";
import { useQueryClient } from "@tanstack/react-query";
import { useArtistFinancials } from "../hooks/useArtistFinancials";
import logger from "../utilities/logger";
import rawAbi from "../abis/ArtistSharesTokenABI.json";
const ArtistSharesTokenABI = rawAbi.abi || rawAbi;

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
    const [showChat, setShowChat] = useState(window.innerWidth >= 768);
    const chartRef = useRef();

    const isDev = process.env.NODE_ENV === "development";
    const log = (prefix, ...args) => isDev && logger.debug(prefix, ...args);

    const artistId = location.state?.artistId || location.state?.spotifyId;
    const { financials, isLoading: financialsLoading, error: financialsError } = useArtistFinancials(artistId);

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
        price: financials.currentPrice !== "N/A" ? parseFloat(financials.currentPrice.replace('$', '')) : 0,
        volume: financials.volume24h !== "N/A" ? parseFloat(financials.volume24h.replace('$', '')) : 0,
        marketCap: financials.marketCap !== "N/A" ? parseFloat(financials.marketCap.replace('$', '')) : 0,
        dailyLiquidity: artistDetails?.dailyLiquidity || 0,
        liquidityPercentage: artistDetails?.liquidityPercentage || 100,
        availableShares: artistDetails?.availableShares || 0,
        nextReset: artistDetails?.nextReset || null,
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
      let requestInProgress = false;

      const initWeb3 = async () => {
        if (!window.ethereum || !window.ethereum.request) {
          setMetaMaskAvailable(false);
          setLoading(false);
          console.error("[INIT] MetaMask not available.");
          return;
        }

        try {
          if (!requestInProgress) {
            requestInProgress = true;
            const accounts = await window.ethereum.request({
              method: "eth_requestAccounts",
            });

            if (!Array.isArray(accounts) || accounts.length === 0) {
              console.warn("[INIT] No MetaMask accounts returned.");
              return;
            }

            const injectedWeb3 = getWeb3();
            const readOnlyWeb3 = getHttpWeb3();
            setWalletWeb3(injectedWeb3);
            setHttpWeb3(readOnlyWeb3);
            setAccount(accounts[0]);
          }
        } catch (err) {
          if (err.message?.includes("JSON-RPC") || err.message?.includes("Internal error")) {
            alert("MetaMask or network error. Please refresh and try again.");
          }
          console.error("[INIT] Web3 initialization failed:", err);
        } finally {
          requestInProgress = false;
        }
      };

      initWeb3();

      if (window.ethereum?.on) {
        const handleAccountsChanged = (accounts) => {
          if (accounts.length > 0) {
            setAccount(accounts[0]);
            log("[WALLET] Switched to account:", accounts[0]);
          } else {
            log("[WALLET] No MetaMask accounts connected.");
          }
        };

        const handleChainChanged = (_chainId) => {
          window.location.reload();
        };

        window.ethereum.on("accountsChanged", handleAccountsChanged);
        window.ethereum.on("chainChanged", handleChainChanged);

        return () => {
          window.ethereum.removeListener("accountsChanged", handleAccountsChanged);
          window.ethereum.removeListener("chainChanged", handleChainChanged);
        };
      }
    }, []);

    useEffect(() => {
      const fetchOrCreateContract = async () => {
        if (!walletWeb3 || !account || !artistId) {
          log("[CONTRACT] Missing web3, account, or artistId.");
          return;
        }

        try {
          let address = contractAddress;
          if (isContractAddress(contractAddress)) {
            setResolvedAddress(contractAddress);
            log("[CONTRACT] Valid address detected:", contractAddress);
            return;
          }

          const factory = getFactoryContract(walletWeb3);
          address = await factory.methods.getTokenByArtistId(artistId).call();

          if (address === "0x0000000000000000000000000000000000000000") {
            if (financials.currentPrice === "N/A" && financials.volume24h === "N/A" && financials.marketCap === "N/A") {
              console.log("[CONTRACT] Creating new artist token...");
              const artist = await SpotifyService.getArtistByContractAddress(contractAddress) || { artistName: "Unknown" };
              const artistName = artist.artistName || "Unknown";
              const artistSymbol = artistName.slice(0, 3).toUpperCase();
              const teamWallet = account;

              const receipt = await factory.methods
                .createArtistToken(artistId, artistName, artistSymbol, teamWallet)
                .send({ from: account });

              if (receipt?.events.ArtistTokenCreated) {
                address = receipt.events.ArtistTokenCreated.returnValues.tokenAddress;
                console.log("[CONTRACT] New token created:", address);
              } else {
                throw new Error("Contract deployment failed — event missing.");
              }

              // Update backend with contract address
              try {
                await updateArtistContractAddress(artistId, { contractAddress: address });
                log("[CONTRACT] Backend updated with contract address:", address);
              } catch (updateError) {
                console.error("[CONTRACT] Failed to update backend:", updateError);
                toast.error("Failed to update artist contract in database.");
              }
            }
          } else {
            log("[CONTRACT] Existing token found:", address);
          }

          setResolvedAddress(address);
        } catch (err) {
          console.error("[CONTRACT] Error resolving or creating contract:", err);
        }
      };

      fetchOrCreateContract();
    }, [walletWeb3, account, artistId, contractAddress, financials]);

    useEffect(() => {
      const fetchArtistData = async () => {
        if (!artistId) {
          logger.error("[fetchArtistData] No artistId provided");
          setLoading(false);
          return;
        }

        try {
          setLoading(true);
          const dbArtist = await SpotifyService.getArtistByContractAddress(contractAddress);

          if (dbArtist && dbArtist.artistId) {
            setArtistDetails(dbArtist);
            setSpotifyData(dbArtist);

            const spotifyArtist = await SpotifyService.getArtistDetailsFromSpotify(dbArtist.artistId);
            if (spotifyArtist) {
              setSpotifyData(spotifyArtist);
              try {
                await updateArtistContractAddress(dbArtist.artistId, { contractAddress });
              } catch (updateError) {
                toast.error("Failed to update artist data in database.");
              }
            }
          } else if (artistId) {
            const spotifyArtist = await SpotifyService.getArtistDetailsFromSpotify(artistId);
            if (spotifyArtist) {
              const newArtistDetails = {
                ...spotifyArtist,
                contractAddress,
              };
              setArtistDetails(newArtistDetails);
              setSpotifyData(spotifyArtist);
              try {
                await updateArtistContractAddress(artistId, { contractAddress });
              } catch (updateError) {
                toast.error("Failed to update artist data in database.");
              }
            } else {
              setArtistDetails({
                artistId: "",
                artistName: "Unknown",
                contractAddress,
                imageUrl: "",
                spotifyUrl: "",
                popularity: 0,
              });
              setSpotifyData(null);
              toast.error("Failed to load artist details from Spotify.");
            }
          } else {
            setArtistDetails({
              artistId: "",
              artistName: "Unknown",
              contractAddress,
              imageUrl: "",
              spotifyUrl: "",
              popularity: 0,
            });
            setSpotifyData(null);
            toast.error("No artist ID provided to load details.");
          }
        } catch (err) {
          logger.error("[fetchArtistData] Error fetching artist details:", err);
          setArtistDetails({
            artistId: "",
            artistName: "Unknown",
            contractAddress,
            imageUrl: "",
            spotifyUrl: "",
            popularity: 0,
          });
          setSpotifyData(null);
          toast.error("Failed to load artist details.");
        } finally {
          setLoading(false);
        }
      };

      fetchArtistData();
    }, [artistId, contractAddress]);

    // NEW: Add this useEffect block here (after the fetchArtistData useEffect)
    useEffect(() => {
      if (!loading && !financialsLoading && !financialsError && artistDetails && resolvedAddress && account && walletWeb3 && httpWeb3) {
        setReadyToTrade(true);
      } else {
        setReadyToTrade(false);
      }
    }, [loading, financialsLoading, financialsError, artistDetails, resolvedAddress, account, walletWeb3, httpWeb3]);

    const onBuySuccess = async (contract, account, count, walletWeb3) => {
      try {
        // Notify backend of the trade
        await axios.post(
          `${import.meta.env.VITE_API_URL}/api/blockchain/buy-shares/${artistId}?amount=${count}&weiValue=${contract._address}`,
          {},
          { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
        );
        // Refetch financials
        await queryClient.invalidateQueries(['financials', artistId]);
        const updatedFinancials = queryClient.getQueryData(['financials', artistId]) || financials;
        setArtistDetails({
          ...artistDetails,
          price: updatedFinancials.currentPrice !== "N/A" ? parseFloat(updatedFinancials.currentPrice.replace('$', '')) : 0,
          volume: updatedFinancials.volume24h !== "N/A" ? parseFloat(updatedFinancials.volume24h.replace('$', '')) : 0,
          marketCap: updatedFinancials.marketCap !== "N/A" ? parseFloat(updatedFinancials.marketCap.replace('$', '')) : 0,
        });
        toast.success(`Successfully bought ${count} share(s).`);
        chartRef.current.updateCandles();
        triggerChartRefresh();
        setTimeout(() => {
          chartRef.current.updateCandles();
          triggerChartRefresh();
        }, 3000);
      } catch (err) {
        console.error("[BUY][POST] Error updating after buy:", err);
        toast.error("Error updating after buy.");
      }
    };

    const handleBuy = async () => {
      if (buying) {
        return;
      }

      if (!readyToTrade || !walletWeb3 || !resolvedAddress || !account) {
        toast.error("Trading not ready. Please ensure account, wallet, and contract are connected.");
        return;
      }

      if (!walletWeb3.utils.isAddress(resolvedAddress)) {
        toast.error("Invalid contract address.");
        return;
      }

      const dollarAmountNum = parseFloat(dollarAmount);
      if (isNaN(dollarAmountNum) || dollarAmountNum <= 0) {
        toast.error("Invalid dollar amount. Must be a positive number.");
        return;
      }

      setBuying(true);
      toast.info("Processing buy transaction...");

      try {
        const result = await safeBuyHandler(walletWeb3, resolvedAddress, account, dollarAmount);
        if (!result.success) {
          toast.error(result.error);
          setBuying(false);
          return;
        }

        const contract = new walletWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
        const amountBigInt = BigInt(result.amount);
        const totalCost = BigInt(result.totalCost);

        let gasLimit;
        try {
          const estimatedGas = await contract.methods
            .buyShares(amountBigInt.toString())
            .estimateGas({
              from: account,
              value: totalCost.toString(),
            });
          gasLimit = Math.max(Math.floor(Number(estimatedGas) * 1.5), 700_000);
        } catch (err) {
          gasLimit = 700_000;
        }

        const tx = await contract.methods.buyShares(amountBigInt.toString()).send({
          from: account,
          value: totalCost.toString(),
          gas: gasLimit.toString(),
        });

        log("[BUY][TX] Success:", tx?.transactionHash);
        await onBuySuccess(contract, account, result.amount, walletWeb3);
      } catch (err) {
        console.error("[BUY] Error:", err);
        toast.error("Unhandled error: " + (err?.message || "Unknown"));
      } finally {
        setBuying(false);
      }
    };

    const onSellSuccess = async (contract, account, count, walletWeb3) => {
    try {
      // Notify backend of the trade
      await axios.post(
        `${import.meta.env.VITE_API_URL}/api/blockchain/sell-shares/${artistId}?amount=${count}&weiValue=${contract._address}`,
        {},
        { headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` } }
      );
      // Refetch financials
      await queryClient.invalidateQueries(['financials', artistId]);
      const updatedFinancials = queryClient.getQueryData(['financials', artistId]) || financials;
      setArtistDetails({
        ...artistDetails,
        price: updatedFinancials.currentPrice !== "N/A" ? parseFloat(updatedFinancials.currentPrice.replace('$', '')) : 0,
        volume: updatedFinancials.volume24h !== "N/A" ? parseFloat(updatedFinancials.volume24h.replace('$', '')) : 0,
        marketCap: updatedFinancials.marketCap !== "N/A" ? parseFloat(updatedFinancials.marketCap.replace('$', '')) : 0,
      });
      toast.success(`Successfully sold ${count} share(s).`);
      chartRef.current.updateCandles();
      triggerChartRefresh();
      setTimeout(() => {
        chartRef.current.updateCandles();
        triggerChartRefresh();
      }, 3000);
    } catch (err) {
      console.error("[SELL][POST] Error updating after sell:", err);
      toast.error("Error updating after sell.");
    }
  };

  const handleSell = async () => {
    if (selling) {
      return;
    }

    if (!readyToTrade || !walletWeb3 || !resolvedAddress || !account) {
      toast.error("Trading not ready. Please ensure account, wallet, and contract are connected.");
      return;
    }

    if (!walletWeb3.utils.isAddress(resolvedAddress)) {
      toast.error("Invalid contract address.");
      return;
    }

    const dollarAmountNum = parseFloat(dollarAmount);
    if (isNaN(dollarAmountNum) || dollarAmountNum <= 0) {
      toast.error("Invalid dollar amount. Must be a positive number.");
      return;
    }

    setSelling(true);
    toast.info("Processing sell transaction...");

    try {
      const result = await safeSellHandler(walletWeb3, resolvedAddress, account, dollarAmount);
      if (!result.success) {
        toast.error(result.error);
        setSelling(false);
        return;
      }

      const contract = new walletWeb3.eth.Contract(ArtistSharesTokenABI, resolvedAddress);
      let estimatedGas, safeGasLimit;
      try {
        estimatedGas = await contract.methods.sellShares(result.amount).estimateGas({ from: account });
        safeGasLimit = Math.max(Math.floor(Number(estimatedGas) * 1.5), 700_000);
      } catch (err) {
        safeGasLimit = 700_000;
      }

      const tx = await contract.methods.sellShares(result.amount).send({
        from: account,
        gas: safeGasLimit,
      });

      log("[SELL][TX] Success:", tx?.transactionHash);
      await onSellSuccess(contract, account, result.amount, walletWeb3);
    } catch (err) {
      console.error("[SELL] Error:", err);
      toast.error("Sell transaction failed: " + (err?.message || "Unknown error."));
    } finally {
      setSelling(false);
    }
  };

  if (!metaMaskAvailable) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <Alert variant="danger" className="text-center">
          <p>MetaMask is not installed. Please install MetaMask to continue.</p>
        </Alert>
      </div>
    );
  }

  if (!loading && !artistDetails) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <h3>Artist not found or data unavailable</h3>
      </div>
    );
  }

  if (loading || financialsLoading) {
    return (
      <div className="d-flex flex-column justify-content-center align-items-center vh-100">
        <Spinner animation="border" role="status" />
        <p className="mt-3">Loading artist details...</p>
      </div>
    );
  }

  if (financialsError) {
    toast.error("Failed to load financial data.");
  }

  return (
    <Container fluid className="px-4 py-5">
      <ToastContainer position="top-right" autoClose={3000} hideProgressBar />
      <Row className="g-4">
        <Col md={8}>
          <Card className="h-100">
            <Card.Body className="chart-card-body">
              <div className="chart-inner-wrapper">
                {artistDetails.contractAddress ? (
                  <ArtistChart
                    ref={chartRef}
                    contractAddress={artistDetails.contractAddress}
                    artistId={artistDetails.artistId}
                    refreshTrigger={chartRefreshKey}
                  />
                ) : (
                  <p>Loading chart...</p>
                )}
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={4}>
          <Card className="h-100">
            <Card.Body>
              <div className="artist-info-container">
                <div className="artist-image">
                  <img
                    src={artistDetails.imageUrl || "/default-artist.jpg"}
                    onError={(e) => (e.target.src = "/default-artist.jpg")}
                    alt="Artist"
                    className="rounded object-fit-cover"
                  />
                </div>
                <div className="artist-metrics">
                  <h4>{artistDetails.artistName || "Loading..."}</h4>
                  <Row>
                    <Col xs={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Total value of all outstanding shares</Tooltip>}
                      >
                        <p className="mb-1">Market Cap: {memoizedArtistDetails.marketCap ? formatMarketCap(memoizedArtistDetails.marketCap) : "N/A"}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Total value of tokens available for trading</Tooltip>}
                      >
                        <p className="mb-1">Daily Liquidity: {memoizedArtistDetails.dailyLiquidity ? formatMarketCap(memoizedArtistDetails.dailyLiquidity) : "N/A"}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Percentage of daily trading limit remaining</Tooltip>}
                      >
                        <ProgressBar
                          now={memoizedArtistDetails.liquidityPercentage || 100}
                          label={
                            memoizedArtistDetails.liquidityPercentage === 100
                              ? "Full liquidity"
                              : `${(memoizedArtistDetails.liquidityPercentage || 100).toFixed(2)}%`
                          }
                          variant={
                            memoizedArtistDetails.liquidityPercentage === 100
                              ? "success"
                              : memoizedArtistDetails.liquidityPercentage >= 50
                              ? "success"
                              : memoizedArtistDetails.liquidityPercentage >= 25
                              ? "warning"
                              : "danger"
                          }
                          className="mb-2"
                        />
                      </OverlayTrigger>
                    </Col>
                    <Col xs={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Trading volume in the last 24 hours</Tooltip>}
                      >
                        <p className="mb-1">24h Volume: {memoizedArtistDetails.volume ? formatVolume(memoizedArtistDetails.volume) : "N/A"}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Shares available for trading</Tooltip>}
                      >
                        <p className="mb-1">Tradable Shares: {memoizedArtistDetails.availableShares ? formatVolume(memoizedArtistDetails.availableShares, true) : "N/A"}</p>
                      </OverlayTrigger>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Time until daily liquidity resets</Tooltip>}
                      >
                        <p className="mb-1">Next Reset: {memoizedArtistDetails.nextReset ? memoizedArtistDetails.nextReset.toLocaleTimeString("en-US", { timeZone: "UTC" }) : "N/A"}</p>
                      </OverlayTrigger>
                    </Col>
                  </Row>
                  <Row>
                    <Col xs={6}>
                      <OverlayTrigger
                        placement="top"
                        overlay={<Tooltip>Artist's Spotify followers</Tooltip>}
                      >
                        <p className="mb-1">Followers: {spotifyData?.followers ? formatVolume(spotifyData.followers, true) : "N/A"}</p>
                      </OverlayTrigger>
                    </Col>
                    <Col xs={6}>
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
              <div className="trade-controls mt-3">
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
                  />
                  {dollarAmount && memoizedArtistDetails.price && (
                    <Form.Text id="share-estimate" className="text-muted">
                      ~{(parseFloat(dollarAmount) / memoizedArtistDetails.price).toFixed(2)} shares
                    </Form.Text>
                  )}
                </Form.Group>
                <div className="trade-buttons">
                  <Button
                    variant="outline-primary"
                    onClick={() => setDollarAmount((prev) => (prev ? (parseFloat(prev) + 50) * 1 : 50).toString())}
                    className="trade-btn"
                  >
                    $50
                  </Button>
                  <Button
                    variant="outline-primary"
                    onClick={() => setDollarAmount((prev) => (prev ? (parseFloat(prev) + 100) * 1 : 100).toString())}
                    className="trade-btn"
                  >
                    $100
                  </Button>
                  <Button
                    variant="outline-primary"
                    onClick={() => setDollarAmount((prev) => (prev ? (parseFloat(prev) + 500) * 1 : 500).toString())}
                    className="trade-btn"
                  >
                    $500
                  </Button>
                  <Button
                    variant="outline-primary"
                    onClick={() => setDollarAmount((prev) => (prev ? (parseFloat(prev) + 1000) * 1 : 1000).toString())}
                    className="trade-btn"
                  >
                    $1,000
                  </Button>
                  <Button
                    variant="success"
                    onClick={handleBuy}
                    disabled={!readyToTrade || buying || financials.currentPrice === "N/A"}
                    className="trade-btn"
                    aria-label="Buy shares"
                  >
                    {buying ? <Spinner size="sm" animation="border" /> : "Buy"}
                  </Button>
                  <Button
                    variant="danger"
                    onClick={handleSell}
                    disabled={!readyToTrade || selling || financials.currentPrice === "N/A"}
                    className="trade-btn"
                    aria-label="Sell shares"
                  >
                    {selling ? <Spinner size="sm" animation="border" /> : "Sell"}
                  </Button>
                </div>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      <Row className="g-4 mt-4">
        <Col md={8}>
          <Card className="h-100">
            <Card.Body className="transactions-card-body">
              <LiveTransactions
                contractAddress={contractAddress}
                walletWeb3={walletWeb3}
                httpWeb3={httpWeb3}
              />
            </Card.Body>
          </Card>
        </Col>
        {showChat && (
          <Col md={4}>
            <Card className="h-100">
              <Card.Body>
                <div className="chatbox">
                  {(() => {
                    const userId = account ? `${account.slice(0, 2)}...${account.slice(-3)}` : "unknown";
                    return <ChatBox artistId={artistDetails.artistId} userId={userId} />;
                  })()}
                </div>
              </Card.Body>
            </Card>
          </Col>
        )}
        <Col md={12} className="text-center">
          <Button
            variant="outline-primary"
            onClick={() => setShowChat(!showChat)}
            className="mt-2"
          >
            {showChat ? "Hide Chat" : "Show Chat"}
          </Button>
        </Col>
      </Row>
    </Container>
  );
};


export default ArtistDetails;
