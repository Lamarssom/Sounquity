import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import {
  getContractAddressFromFactory,
  createArtistTokenOnFactory,
  getPlatformAddress,
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

const ArtistCard = ({ artist, onViewDetails }) => {
  const navigate = useNavigate();
  const [platformAddress, setPlatformAddress] = useState(null);

  const { currentPrice, volume, marketCap } = {
    currentPrice: artist.currentPrice || "N/A",
    volume: artist.totalVolume || "N/A",
    marketCap: artist.marketCap || "N/A",
  };

  useEffect(() => {
    const fetchPlatformAddress = async () => {
      try {
        const address = await getPlatformAddress();
        setPlatformAddress(address);
      } catch (error) {
        console.error("[ArtistCard] Error fetching platform address:", error);
      }
    };
    fetchPlatformAddress();
  }, []);

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

        // Update backend with contract address
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
    <div className="artist-card mb-4">
      <img
        src={isValidUrl ? artist.imageUrl : placeholderImage}
        alt={artist.name || artist.artistName || "Artist"}
        onError={(e) => {
          e.target.onerror = null;
          e.target.src = placeholderImage;
        }}
        className="artist-image"
      />
      <div className="card-body">
        <h5 className="card-title">{artist.name || artist.artistName || "Unknown Artist"}</h5>
        <p className="card-text">
          <strong>Symbol:</strong> {artist.symbol || generateSymbol(artist.name || artist.artistName || "Unknown")}
          <br />
          <strong>Current Price:</strong> {currentPrice}
          <br />
          <strong>24h Volume:</strong> {volume}
          <br />
          <strong>Market Cap:</strong> {marketCap}
          <br />
          <strong>Popularity:</strong> {artist.popularity || "—"}
        </p>
        {platformAddress && (
          <p className="card-text" style={{ fontSize: "12px", wordBreak: "break-word" }}>
            <strong>Platform:</strong> {platformAddress}
          </p>
        )}
        <button className="btn btn-success" onClick={handleViewDetails}>
          View Details
        </button>
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