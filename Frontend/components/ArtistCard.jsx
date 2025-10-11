import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { LazyLoadImage } from "react-lazy-load-image-component";
import "react-lazy-load-image-component/src/effects/blur.css";
import {
  getContractAddressFromFactory,
  createArtistTokenOnFactory,
} from "../utilities/blockchain";
import { getAuthHeaders } from "../utilities/auth";
import SpotifyService from "../services/SpotifyService";
import "../styles/ArtistCard.css";

const isValidAddress = (address) => /^0x[a-fA-F0-9]{40}$/.test(address);

const generateSymbol = (name) => {
  const safeName = name || "Unknown";
  const base = safeName.substring(0, 3).toUpperCase();
  const randomSuffix = Math.floor(Math.random() * 100).toString().padStart(2, "0");
  return `${base}${randomSuffix}`;
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

const ArtistCard = ({ artist, onViewDetails }) => {
  const navigate = useNavigate();
  const [financials, setFinancials] = useState({
    currentPrice: "N/A",
    volume: "N/A",
    marketCap: "N/A",
  });

  useEffect(() => {
    const fetchFinancials = async () => {
      try {
        const artistId = artist.spotifyId || artist.id || artist.artistId;
        if (!artistId) {
          console.warn("[ArtistCard] No artistId for financials fetch, artist:", artist);
          return;
        }
        const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/blockchain/financials/${artistId}`, {
          headers: getAuthHeaders(),
        });
        const fetchedFinancials = {
          currentPrice: response.data.currentPrice || "N/A",
          volume: response.data.volume24h || "N/A",
          marketCap: response.data.marketCap || "N/A",
        };
        if (fetchedFinancials.marketCap !== "N/A") {
          console.log(`[ArtistCard] Financials fetched for artistId ${artistId}:`, fetchedFinancials);
        }
        setFinancials(fetchedFinancials);
      } catch (error) {
        console.error(`[ArtistCard] Error fetching financials for artistId ${artist.spotifyId || artist.id || artist.artistId}:`, error.response ? error.response.data : error.message);
      }
    };

    fetchFinancials();
  }, [artist]);

  if (!artist) return <div>Loading artist info...</div>;

  const placeholderImage = "/default-artist.jpg";
  const isValidUrl = artist.imageUrl && artist.imageUrl.trim() !== "";

  const handleViewDetails = async () => {
    const artistId = artist.spotifyId || artist.id || artist.artistId;
    const artistName = artist.name || artist.artistName || "Unknown Artist";
    let artistSymbol = artist.symbol || generateSymbol(artistName);

    console.log("[handleViewDetails] Artist ID:", artistId, "Name:", artistName, "Symbol:", artistSymbol);

    if (!artistId || !artistName || !artistSymbol) {
      console.warn("[handleViewDetails] Missing required artist fields.");
      return;
    }

    try {
      let contractAddress = artist.contractAddress;
      let popularity = artist.popularity || 50;

      if (!contractAddress) {
        contractAddress = await getContractAddressFromFactory(artistId);
        console.log("[handleViewDetails] Fetched contract address:", contractAddress);
      }

      if (!contractAddress || contractAddress === "0x0000000000000000000000000000000000000000") {
        console.log("[handleViewDetails] No contract found, deploying new one...");
        try {
          const spotifyData = await SpotifyService.getArtistDetailsFromSpotify(artistId);
          if (spotifyData && typeof spotifyData.popularity === "number") {
            popularity = spotifyData.popularity;
            console.log(`[handleViewDetails] Fetched popularity for ${artistName}: ${popularity}`);
          } else {
            console.warn("[handleViewDetails] No valid Spotify data, using default popularity:", popularity);
          }
        } catch (spotifyError) {
          console.error("[handleViewDetails] Failed to fetch Spotify data:", spotifyError.response?.data || spotifyError.message);
          console.warn("[handleViewDetails] Using default popularity:", popularity);
        }

        contractAddress = await createArtistTokenOnFactory(artistId, artistName, artistSymbol, popularity);
        console.log("[handleViewDetails] New contract deployed at:", contractAddress);

        if (!isValidAddress(contractAddress)) {
          console.error("[handleViewDetails] Invalid contract address:", contractAddress);
          return;
        }

        try {
          const response = await axios.put(
            `${import.meta.env.VITE_API_URL}/api/artists/${artistId}/update-contract`,
            { contractAddress },
            { headers: getAuthHeaders() }
          );
          console.log("[handleViewDetails] Backend response:", response.data);
        } catch (error) {
          console.error(
            "[handleViewDetails] Failed to update backend:",
            error.response ? error.response.data : error.message
          );
          console.warn("[handleViewDetails] Proceeding to navigation despite backend update failure");
        }
      }

      artist.contractAddress = contractAddress;
      console.log(`[handleViewDetails] Triggering navigation for contract address: ${contractAddress} with popularity: ${popularity}`);
      onViewDetails({ ...artist, contractAddress });
    } catch (error) {
      console.error("[handleViewDetails] Error during contract fetch/creation:", error);
    }
  };

  return (
    <div className="artist-card card card-glass mb-4">
      <div className="row g-0">
        <div className="col-4">
          <LazyLoadImage
            src={isValidUrl ? artist.imageUrl : placeholderImage}
            alt={artist.name || artist.artistName || "Artist"}
            className="artist-image img-fluid"
            effect="blur"
            placeholderSrc={placeholderImage}
            onError={(e) => {
              e.target.onerror = null;
              e.target.src = placeholderImage;
            }}
          />
        </div>
        <div className="col-8">
          <div className="card-body">
            <h5 className="card-title">{artist.name || artist.artistName || "Unknown Artist"}</h5>
            <p className="card-text">
              <strong>Symbol:</strong> {artist.symbol || generateSymbol(artist.name || artist.artistName || "Unknown")}
              <br />
              <strong>Current Price:</strong> {financials.currentPrice}
              <br />
              <strong>24h Volume:</strong> {financials.volume}
              <br />
              <strong>Market Cap:</strong> {financials.marketCap}
              <br />
              <strong>Popularity:</strong> {artist.popularity || "—"}
            </p>
            <button className="btn btn-gradient w-100" onClick={handleViewDetails}>
              View Details
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

ArtistCard.propTypes = {
  artist: PropTypes.shape({
    id: PropTypes.string,
    spotifyId: PropTypes.string,
    artistId: PropTypes.string,
    contractAddress: PropTypes.string,
    name: PropTypes.string,
    artistName: PropTypes.string,
    symbol: PropTypes.string,
    imageUrl: PropTypes.string,
    popularity: PropTypes.number,
    currentPrice: PropTypes.string,
    totalVolume: PropTypes.string,
    marketCap: PropTypes.string,
  }).isRequired,
  onViewDetails: PropTypes.func.isRequired,
};

export default ArtistCard;
