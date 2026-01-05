import React, { useEffect, useRef, useState, memo } from "react";
import { Card, Form, Button, Alert } from "react-bootstrap";
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { getAuthHeaders } from "../utilities/auth";
import logger from "../utilities/logger";
import "../styles/ChatBox.css";

const ChatBox = ({ contractAddress, userId }) => {
  const [messages, setMessages] = useState([]);
  const [artistId, setArtistId] = useState(null);
  const [error, setError] = useState(null);
  const [newMessage, setNewMessage] = useState("");
  const stompClient = useRef(null);
  const messagesEndRef = useRef(null);

  // Fetch artistId
  useEffect(() => {
    if (!contractAddress) {
      logger.error("ChatBox", "Missing contractAddress");
      setError("Missing contract address");
      return;
    }
    axios.get(`${import.meta.env.VITE_API_URL}/artists/by-contract/${contractAddress}`)
      .then(response => {
        if (response.data && response.data.artistId) {
          console.log("[ChatBox] Fetched artistId:", response.data.artistId); // Debug log
          setArtistId(response.data.artistId);
          setError(null);
        } else {
          logger.error("ChatBox", `Invalid response: ${JSON.stringify(response.data)}`);
          setError("Failed to fetch artist ID");
        }
      })
      .catch(error => {
        const errorMessage = error.response ? error.response.data.message : error.message;
        logger.error("ChatBox", `Error fetching artistId: ${errorMessage}`);
        setError(`Error: ${errorMessage}`);
      });
  }, [contractAddress]);

  // Fetch historical messages
  useEffect(() => {
    if (!artistId) return;

    axios.get(`${import.meta.env.VITE_API_URL}/messages?artistId=${artistId}`, {
      headers: getAuthHeaders()
    })
      .then(response => {
        const formattedMessages = response.data.map(msg => ({
          id: msg.id || Date.now(),
          user: msg.userId || "0x0000...0000",
          content: msg.message || "",
          timestamp: new Date(msg.timestamp).getTime() || Date.now(),
        }));
        setMessages(formattedMessages.slice(0, 50)); // Limit to 50 messages
        console.log("ChatBox", `Fetched messages: ${formattedMessages.length}`);
      })
      .catch(error => {
        const errorMessage = error.response?.status === 401
          ? "Please log in to view messages."
          : `Error fetching messages: ${error.response ? error.response.data.message : error.message}`;
        logger.error("ChatBox", errorMessage);
        setError(errorMessage);
      });
  }, [artistId]);

  // WebSocket for real-time messages
  useEffect(() => {
    if (!artistId) return;

    const client = new Client({
      webSocketFactory: () => {
        const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
        const sockJsUrl = apiUrl.replace('/api', '') + '/ws';
        return new SockJS(sockJsUrl, null, {
          transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
          timeout: 15000,
        });
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => logger.debug("ChatBox", `STOMP: ${str}`),
    });

    client.onConnect = (frame) => {
      logger.info("ChatBox", `WebSocket connected: ${JSON.stringify(frame)}`);
      client.subscribe(`/topic/messages/${artistId}`, (message) => {
        try {
          const msg = JSON.parse(message.body);
          const timestamp = new Date(msg.timestamp).getTime() || Date.now();
          const formattedMessage = {
            id: msg.id || Date.now(),
            user: msg.userId || "0x0000...0000",
            content: msg.message || "",
            timestamp,
          };
          setMessages(prev => [formattedMessage, ...prev].slice(0, 50)); // Add new, limit to 50
          console.log("ChatBox", `New message: ${JSON.stringify(formattedMessage)}`);
        } catch (err) {
          logger.error("ChatBox", `Error processing message: ${err.message}`);
          setError(`Error processing message: ${err.message}`);
        }
      }, { 'ack': 'client' });
    };

    client.onStompError = (frame) => {
      logger.error("ChatBox", `STOMP Error: ${frame.headers?.message || 'Unknown error'}`);
      setError(`WebSocket Error: ${frame.headers?.message || 'Unknown error'}`);
    };

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
        logger.info("ChatBox", "WebSocket deactivated");
      }
    };
  }, [artistId]);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // Handle message submission
  const handleSendMessage = (e) => {
    e.preventDefault();
    if (!newMessage.trim() || !artistId) {
      setError("Message or artist ID is missing");
      console.log("ChatBox", "Message or artist ID is missing");
      return;
    }

    const message = {
      artistId: artistId,
      userId: userId || "0xAnonymous",
      message: newMessage.trim(),
      timestamp: null
    };

    console.log("[ChatBox] Sending message payload:", message);

    axios.post(`${import.meta.env.VITE_API_URL}/messages`, message, {
      headers: getAuthHeaders()
    })
      .then(() => {
        setNewMessage("");
        console.log("ChatBox", "Message sent successfully");
      })
      .catch(error => {
        const errorMessage = error.response?.data || error.message;
        console.error("[ChatBox] Error response:", errorMessage);
        console.log("ChatBox", `Error sending message: ${JSON.stringify(errorMessage)}`);
        setError(`Error sending message: ${JSON.stringify(errorMessage)}`);
      });
  };

  const MessageRow = memo(({ msg }) => (
    <div className="chat-message">
      <span className="user-address">
        <a
          href={`https://etherscan.io/address/${msg.user}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-light"
        >
          {msg.user.slice(0, 6)}...{msg.user.slice(-4)}
        </a>
      </span>
      <span className="message-content">{msg.content}</span>
      <span className="message-time">{getRelativeTime(msg.timestamp)}</span>
    </div>
  ));

  return (
    <Card className="card-glass h-100 chat-box">
      <Card.Header as="h3" className="text-warning mb-0">Chit-Chat</Card.Header>
      <Card.Body className="p-3 d-flex flex-column">
        {error && <Alert variant="danger" className="m-3">{error}</Alert>}
        <div className="chat-messages bg-dark">
          {messages.length === 0 ? (
            <p className="no-messages">No messages yet—start a conversation!</p>
          ) : (
            messages.map(msg => <MessageRow key={msg.id} msg={msg} />)
          )}
          <div ref={messagesEndRef} />
        </div>
        <Form onSubmit={handleSendMessage} className="mt-3">
          <div className="d-flex">
            <Form.Control
              type="text"
              value={newMessage}
              onChange={(e) => setNewMessage(e.target.value)}
              placeholder="Type a message..."
              className="me-2"
            />
            <Button type="submit" variant="gradient">Send</Button>
          </div>
        </Form>
      </Card.Body>
    </Card>
  );
};

const getRelativeTime = (timestamp) => {
  let date;

  if (typeof timestamp === "string") {
    date = new Date(timestamp);
  }

  else if (typeof timestamp === "number") {
    date = new Date(timestamp);
  }

  else if (Array.isArray(timestamp) && timestamp.length >= 6) {
    date = new Date(
      timestamp[0],
      timestamp[1] - 1,
      timestamp[2],
      timestamp[3] || 0,
      timestamp[4] || 0,
      timestamp[5] || 0
    );
  } else {
    return "Just now";
  }

  if (isNaN(date.getTime())) return "Just now";

  const now = new Date();
  const diff = Math.floor((now - date) / 1000);

  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return date.toLocaleDateString();
};

export default ChatBox;