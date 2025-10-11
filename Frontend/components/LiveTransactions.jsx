import React, { useEffect, useRef, useState, memo } from "react";
import { Card, Table, Alert, Dropdown } from "react-bootstrap";
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { getAuthHeaders } from "../utilities/auth";
import logger from "../utilities/logger";
import "../styles/LiveTransactions.css";

const LiveTransactions = ({ contractAddress }) => {
  const [trades, setTrades] = useState([]);
  const [artistId, setArtistId] = useState(null);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState("All"); // Defaults to "All" to render all transactions initially
  const stompClient = useRef(null);

  useEffect(() => {
    logger.info("LiveTransactions", `Received contractAddress: ${contractAddress}`);
    if (!contractAddress) {
      logger.error("LiveTransactions", "Missing contractAddress");
      setError("Missing contract address");
      return;
    }
    // Fetch artistId from backend
    axios.get(`http://localhost:8080/api/artists/by-contract/${contractAddress}`)
      .then(response => {
        logger.info("LiveTransactions", `API Response for /api/artists/by-contract/${contractAddress}: ${JSON.stringify(response.data)}`);
        if (response.data && response.data.artistId) {
          setArtistId(response.data.artistId);
          logger.info("LiveTransactions", `Fetched artistId: ${response.data.artistId} for contractAddress: ${contractAddress}`);
          setError(null);
        } else {
          logger.error("LiveTransactions", `Invalid response from API: Expected 'artistId' field, got: ${JSON.stringify(response.data)}`);
          setError("Failed to fetch artist ID: Invalid response");
        }
      })
      .catch(error => {
        const errorMessage = error.response ? error.response.data.message || error.response.statusText : error.message;
        logger.error("LiveTransactions", `Error fetching artistId for contractAddress ${contractAddress}: ${errorMessage}`);
        setError(`Error fetching artist ID: ${errorMessage}`);
      });
  }, [contractAddress]);

  // Fetch historical trades
  useEffect(() => {
    if (!artistId) return;

    axios.get(`http://localhost:8080/api/trades/artist/${artistId}`, {
      headers: getAuthHeaders()
    })
      .then(response => {
        logger.info("LiveTransactions", `Fetched historical trades for artistId ${artistId}: ${JSON.stringify(response.data)}`);
        const formattedTrades = response.data.map(trade => ({
          type: trade.eventType || "UNKNOWN",
          user: trade.buyerOrSeller || "0x0000000000000000000000000000000000000000",
          amountInUsd: parseFloat(trade.amountInUsd || 0).toFixed(2),
          priceInUsd: parseFloat(trade.priceInUsd || 0).toFixed(6),
          timestamp: trade.timestamp && Array.isArray(trade.timestamp) && trade.timestamp.length >= 6
            ? new Date(trade.timestamp[0], trade.timestamp[1] - 1, trade.timestamp[2], trade.timestamp[3] || 0, trade.timestamp[4] || 0, trade.timestamp[5] || 0).getTime()
            : Date.now(),
          txHash: trade.txHash || "N/A",
        }));
        setTrades(formattedTrades.slice(0, 20));
        logger.info("LiveTransactions", `Set historical trades: ${JSON.stringify(formattedTrades)}`);
        setError(null);
      })
      .catch(error => {
        const errorMessage = error.response?.status === 401
          ? "Please log in to view historical trades."
          : `Error fetching historical trades: ${error.response ? error.response.data.message || error.response.statusText : error.message}`;
        logger.error("LiveTransactions", `Error fetching historical trades for artistId ${artistId}: ${errorMessage}`);
        setError(errorMessage);
      });
  }, [artistId]);

  useEffect(() => {
    if (!artistId) {
      logger.warn("LiveTransactions", "Waiting for artistId");
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws', null, {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
        timeout: 15000,
      }),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        logger.debug("LiveTransactions", `STOMP Debug: ${str}`);
      },
    });

    client.onConnect = (frame) => {
      logger.info("LiveTransactions", `Successfully connected to WebSocket: ${JSON.stringify(frame)}`);
      const subscription = client.subscribe(`/topic/trades/${artistId}`, (message) => {
        logger.info("LiveTransactions", `Received message on /topic/trades/${artistId}: ${message.body}`);
        try {
          const trade = JSON.parse(message.body);
          let timestamp;
          if (trade.timestamp && Array.isArray(trade.timestamp) && trade.timestamp.length >= 6) {
            timestamp = new Date(
              trade.timestamp[0],
              trade.timestamp[1] - 1,
              trade.timestamp[2],
              trade.timestamp[3] || 0,
              trade.timestamp[4] || 0,
              trade.timestamp[5] || 0
            ).getTime();
          } else {
            logger.warn("LiveTransactions", `Invalid or missing timestamp in trade: ${JSON.stringify(trade)}`);
            timestamp = Date.now();
          }
          if (isNaN(timestamp)) {
            logger.warn("LiveTransactions", `Timestamp parsing failed, using Date.now(): ${JSON.stringify(trade)}`);
            timestamp = Date.now();
          }
          const formattedTrade = {
            type: trade.eventType || "UNKNOWN",
            user: trade.buyerOrSeller || "0x0000000000000000000000000000000000000000",
            amountInUsd: parseFloat(trade.amountInUsd || 0).toFixed(2),
            priceInUsd: parseFloat(trade.priceInUsd || 0).toFixed(6),
            timestamp,
            txHash: trade.txHash || "N/A",
          };
          setTrades((prev) => {
            const newTrades = [formattedTrade, ...prev].slice(0, 20);
            logger.info("LiveTransactions", `Updated trades state: ${JSON.stringify(newTrades)}`);
            return newTrades;
          });
          if (message.headers['message-id']) {
            client.ack(message.headers['message-id'], message.headers.subscription);
          }
        } catch (err) {
          logger.error("LiveTransactions", `Error processing trade message: ${err.message}`);
          setError(`Error processing trade: ${err.message}`);
        }
      }, { 'ack': 'client' });
    };

    client.onStompError = (frame) => {
      logger.error("LiveTransactions", `STOMP Error: ${frame.headers?.message || 'Unknown error'}`);
      setError(`STOMP Error: ${frame.headers?.message || 'Unknown error'}`);
    };

    client.onWebSocketError = (error) => {
      logger.error("LiveTransactions", `WebSocket Error: ${error.message || error}`);
      setError(`WebSocket Error: ${error.message || error}`);
    };

    client.onWebSocketClose = (event) => {
      logger.warn("LiveTransactions", `WebSocket Closed: ${event.reason || 'No reason provided'}`);
      setError(`WebSocket Closed: ${event.reason || 'No reason provided'}`);
    };

    client.beforeConnect = () => {
      logger.info("LiveTransactions", `Attempting to connect to WebSocket for artistId: ${artistId}`);
    };

    client.activate();
    logger.info("LiveTransactions", `Activating WebSocket client for artistId: ${artistId}`);
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
        logger.info("LiveTransactions", "WebSocket client deactivated");
      }
    };
  }, [artistId]);

  const buySellRatio = { buys: 0, sells: 0 }; // Initialize to avoid undefined
  trades.forEach(t => {
    if (t.type === "BUY") buySellRatio.buys++;
    if (t.type === "SELL") buySellRatio.sells++;
  });
  const total = buySellRatio.buys + buySellRatio.sells;
  const buyPercentage = total ? (buySellRatio.buys / total * 100).toFixed(0) : 50;

  const filteredTrades = trades
    .sort((a, b) => b.timestamp - a.timestamp)
    .filter(t => 
      filter === "All" ? true :
      filter === "Buys" ? t.type === "BUY" :
      filter === "Sells" ? t.type === "SELL" :
      filter === "Whales" ? parseFloat(t.amountInUsd) >= 500 : false
    );

  const TradeRow = memo(({ t, i }) => (
    <tr key={t.txHash || i} className={i === 0 ? "recent-trade" : ""}>
      <td className={t.type === "BUY" ? "text-success" : "text-danger"}>
        {t.type} {i === 0 && (t.type === "BUY" ? <span className="arrow">↑</span> : t.type === "SELL" ? <span className="arrow">↓</span> : "")}
      </td>
      <td>
        <a
          href={`https://etherscan.io/address/${t.user}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-light"
        >
          {t.user.slice(0, 6)}...{t.user.slice(-4)}
        </a>
      </td>
      <td>${t.amountInUsd}</td>
      <td>${t.priceInUsd}</td>
      <td>{getRelativeTime(t.timestamp)}</td>
      <td>
        <a
          href={`https://etherscan.io/tx/${t.txHash}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-light"
        >
          {t.txHash.slice(0, 10)}...
        </a>
      </td>
    </tr>
  ));

  return (
    <Card className="card-glass h-100 live-transactions">
      <Card.Header as="h3" className="text-warning mb-0">Live Trades</Card.Header>
      <Card.Body className="p-3">
        <div className="hype-meter mb-3">
          <span>Hype Meter: {buyPercentage}% Fans Buying</span>
          <div className="progress progress-sm">
            <div
              className="progress-bar"
              style={{ width: `${buyPercentage}%`, backgroundColor: 'var(--sounquity-green)' }}
            ></div>
            <div
              className="progress-bar bg-danger"
              style={{ width: `${100 - buyPercentage}%` }}
            ></div>
          </div>
        </div>
        <div className="trade-filters mb-2">
          <Dropdown>
            <Dropdown.Toggle variant="outline-light" size="sm">
              Filter: {filter}
            </Dropdown.Toggle>
            <Dropdown.Menu>
              <Dropdown.Item onClick={() => setFilter("All")}>All</Dropdown.Item>
              <Dropdown.Item onClick={() => setFilter("Buys")}>Buys</Dropdown.Item>
              <Dropdown.Item onClick={() => setFilter("Sells")}>Sells</Dropdown.Item>
              <Dropdown.Item onClick={() => setFilter("Whales")}>Whales ($500+)</Dropdown.Item>
            </Dropdown.Menu>
          </Dropdown>
        </div>
        {error && <Alert variant="danger" className="m-3">{error}</Alert>}
        <div className="trade-table-container bg-dark">
          {filteredTrades.length === 0 ? (
            <p className="no-trades">No trades yet—be the first fan!</p>
          ) : (
            <Table className="trade-table table-dark">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Fan</th>
                  <th>Amount ($)</th>
                  <th>Price ($)</th>
                  <th>Time</th>
                  <th>TX Hash</th>
                </tr>
              </thead>
              <tbody>
                {filteredTrades.map((t, i) => <TradeRow key={t.txHash || i} t={t} i={i} />)}
              </tbody>
            </Table>
          )}
        </div>  
      </Card.Body>
    </Card>
  );
};

const getRelativeTime = (timestamp) => {
  const now = new Date();
  const date = new Date(timestamp);
  if (isNaN(date.getTime())) {
    logger.warn("LiveTransactions", `Invalid timestamp in getRelativeTime: ${timestamp}`);
    return "Just now";
  }
  const diff = Math.floor((now - date) / 1000);
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  return date.toLocaleTimeString('en-US', { hour12: true, hour: 'numeric', minute: '2-digit', timeZone: 'Africa/Lagos' });
};

export default LiveTransactions;
