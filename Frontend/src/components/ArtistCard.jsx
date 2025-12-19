import React, { useEffect, useState, useRef } from "react";
import { LazyLoadImage } from "react-lazy-load-image-component";
import "react-lazy-load-image-component/src/effects/blur.css";
import axios from "axios";
import { getAuthHeaders } from "../utilities/auth";
import { toast } from "react-toastify";
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import "../styles/ArtistCard.css";

const generateSymbol = (name) => {
  if (!name) return "ARTIST";
  const clean = name.replace(/[^a-zA-Z]/g, "").toUpperCase();
  return clean.length >= 3 ? clean.substring(0, 7) : clean;
};

const ArtistCard = ({ artist, onViewDetails }) => {
  const [financials, setFinancials] = useState({
    currentPrice: "N/A",
    volume: "N/A",
    marketCap: "N/A",
  });

  const artistId = artist.spotifyId || artist.id || artist.artistId;
  const stompClientRef = useRef(null);

  // Initial fetch
  useEffect(() => {
    if (!artistId) return;

    const fetchFinancials = async () => {
      try {
        const response = await axios.get(
          `${import.meta.env.VITE_API_URL}/blockchain/financials/${artistId}`,
          { headers: getAuthHeaders() }
        );
        setFinancials({
          currentPrice: response.data.currentPrice || "N/A",
          volume: response.data.volume24h || "N/A",
          marketCap: response.data.marketCap || "N/A",
        });
      } catch (err) {
        console.error("Failed to fetch financials for card:", err);
      }
    };

    fetchFinancials();
  }, [artistId]);

  // WebSocket subscription for live updates
  useEffect(() => {
    if (!artistId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000,
      debug: () => {}, // Silence debug spam
    });

    client.onConnect = () => {
      client.subscribe(`/topic/financials/${artistId}`, (msg) => {
        try {
          const data = JSON.parse(msg.body);
          setFinancials({
            currentPrice: data.currentPrice || "N/A",
            volume: data.volume24h || "N/A",
            marketCap: data.marketCap || "N/A",
          });
        } catch (err) {
          console.error("Failed to parse financials update:", err);
        }
      });
    };

    client.activate();
    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [artistId]);

  const handleViewDetails = () => {
    const contractAddress = artist.contractAddress?.trim();

    if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000" || contractAddress === "") {
      toast.info("This artist’s shares are not deployed yet.", {
        position: "top-center",
        autoClose: 4000,
        style: { background: "#222", color: "var(--sounquity-yellow)", fontWeight: "bold" }
      });
      return;
    }

    onViewDetails({ ...artist, contractAddress });
  };

  const placeholderImage = "/default-artist.jpg";
  const isValidUrl = artist.imageUrl && artist.imageUrl.trim() !== "";

  const isDeployed = artist.contractAddress && 
    artist.contractAddress.trim() !== "" && 
    artist.contractAddress !== "0x0000000000000000000000000000000000000000";

  return (
    <div className="artist-card mb-4">
      <div className="row g-0">
        <div className="col-4 d-flex align-items-center justify-content-center p-3">
          <div className="image-container">
            <LazyLoadImage
              src={isValidUrl ? artist.imageUrl : placeholderImage}
              alt={artist.name || artist.artistName}
              effect="blur"
              placeholderSrc={placeholderImage}
              className="rounded-image"
              onError={(e) => { e.target.src = placeholderImage; }}
            />
          </div>
        </div>

        <div className="col-8">
          <div className="card-body d-flex flex-column h-100">
            <h5 className="card-title mb-2" style={{ 
              fontWeight: "700", 
              fontSize: "1.3rem",
              color: "var(--sounquity-yellow)",
              textShadow: "0 0 8px rgba(255,215,0,0.4)"
            }}>
              {artist.name || artist.artistName || "Unknown Artist"}
            </h5>

            <p className="card-text small text-light grow">
              <strong style={{ color: "var(--sounquity-yellow)" }}>Symbol:</strong> ${generateSymbol(artist.name || artist.artistName)}<br/>
              <strong style={{ color: "var(--sounquity-yellow)" }}>Price:</strong> {financials.currentPrice}<br/>
              <strong style={{ color: "var(--sounquity-yellow)" }}>Volume:</strong> {financials.volume}<br/>
              <strong style={{ color: "var(--sounquity-yellow)" }}>Market Cap:</strong> {financials.marketCap}<br/>
              <strong style={{ color: "var(--sounquity-yellow)" }}>Popularity:</strong> {artist.popularity || "—"}
            </p>

            <button 
              className="btn btn-gradient w-100 mt-auto" 
              onClick={handleViewDetails}
              style={{
                opacity: isDeployed ? 1 : 0.6,
                cursor: isDeployed ? "pointer" : "not-allowed",
                transition: "all 0.3s ease"
              }}
              disabled={!isDeployed}
            >
              View Details
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ArtistCard;