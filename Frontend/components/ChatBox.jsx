import React, { useState, useEffect, useCallback } from "react";
import axios from "axios";
import logger from "../utilities/logger"; // Import logger
import "../styles/ChatBox.css";

const ChatBox = ({ artistId, userId }) => {
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Fetch messages from backend API
  const fetchMessages = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/messages?artistId=${artistId}`);
      const apiMessages = Array.isArray(response.data) ? response.data : [];
    //  logger.debug("ChatBox", "Fetched messages:", apiMessages);
      // Merge with existing messages to avoid duplicates (based on id)
      setMessages(prev => {
        const prevIds = new Set(prev.map(msg => msg.id));
        const newMessages = apiMessages.filter(msg => !prevIds.has(msg.id));
        return [...prev, ...newMessages].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
      });
      logger.info("ChatBox", "Successfully fetched messages for artistId:", artistId);
    } catch (err) {
    //  logger.error("ChatBox", "Error fetching messages:", err.response?.data?.message || err.message);
    //  setError("Failed to load messages: " + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  }, [artistId]);

  useEffect(() => {
    fetchMessages(); // Initial fetch
    const interval = setInterval(fetchMessages, 15000); // Poll every 15 seconds
    return () => clearInterval(interval);
  }, [fetchMessages]);

  // Send message to backend API
  const sendMessage = useCallback(async (newMessage) => {
    try {
      const token = localStorage.getItem('token');
      await axios.post(
        `${import.meta.env.VITE_API_URL}/api/messages`,
        newMessage,
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      logger.info("ChatBox", "Message sent successfully for artistId:", artistId);
      // Refresh messages after sending
      fetchMessages();
    } catch (err) {
      logger.error("ChatBox", "Error sending message:", err.response?.data?.message || err.message);
      setError("Failed to send message: " + (err.response?.data?.message || err.message));
    }
  }, [fetchMessages, artistId]);

  const handleSendMessage = () => {
    if (message && userId) {
      const newMessage = { artistId, userId, message, timestamp: new Date().toISOString() };
      setMessage("");
      sendMessage(newMessage);
    }
  };

  return (
    <div className="chatbox">
      {loading ? (
        <div className="loading-container">
          <div className="loading-spinner"></div>
          <p>Loading messages...</p>
        </div>
      ) : error ? (
        <div className="error-container">{error}</div>
      ) : messages.length > 0 ? (
        <div className="messages">
          {messages.map((msg) => (
            <div key={msg.id} className="message">
              <span className="user">{msg.userId}: </span>
              {msg.message} <span className="timestamp">({new Date(msg.timestamp).toLocaleTimeString()})</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="no-messages">No messages yet.</div>
      )}
      <div className="input-container">
        <input
          type="text"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Type a message..."
          disabled={!userId}
          className="chat-input"
        />
        <button
          onClick={handleSendMessage}
          disabled={!userId || !message}
          className="send-button"
        >
          Send
        </button>
      </div>
    </div>
  );
};

export default ChatBox;