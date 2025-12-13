import React, { useState, useEffect, useMemo } from "react";
import "../styles/Home.css";
import SearchBar from "../components/SearchBar";
import ArtistCard from "../components/ArtistCard";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { getAuthHeaders } from "../utilities/auth";
import { getWeb3 } from "../utilities/web3.js";
import ArtistSharesFactory from "../abis/ArtistSharesFactory.json";
import { useBatchArtistFinancials } from "../hooks/useArtistFinancials";
import { FACTORY_CONTRACT_ADDRESS } from "../utilities/config.js";
import styles from '../styles/SearchBar.module.css';

const Home = ({ message }) => {
  const [artists, setArtists] = useState([]);
  const [searchResults, setSearchResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const web3 = useMemo(() => getWeb3(), []);
  const factoryAddress = FACTORY_CONTRACT_ADDRESS;

  const factoryContract = useMemo(() => {
    return new web3.eth.Contract(ArtistSharesFactory.abi, factoryAddress);
  }, [web3]);

  const artistIds = artists.map(artist => artist.artistId).filter(id => id);
  const { financials: batchFinancials, isLoading: financialsLoading } = useBatchArtistFinancials(artistIds);

  useEffect(() => {
    const fetchArtists = async () => {
      try {
        console.log("Fetching artists from /api/artists");
        const response = await axios.get("http://localhost:8080/api/artists", {
          headers: {
            "Content-Type": "application/json",
          },
          withCredentials: true, // Include credentials for CORS
        });
        console.log("Received artists:", response.data);

        const artistsWithContractAddress = await Promise.all(
          response.data.map(async (artist) => {
            try {
              if (!artist.artistId) {
                console.warn("Missing artistId for artist:", artist);
                return { ...artist, contractAddress: null };
              }

              const contractAddress = await factoryContract.methods
                .getTokenByArtistId(artist.artistId)
                .call();
              console.log(`Contract address for ${artist.artistName}: ${contractAddress}`);

              return { ...artist, contractAddress };
            } catch (err) {
              console.error(`Error fetching contract address for artist ${artist.artistName}:`, err);
              return { ...artist, contractAddress: null };
            }
          })
        );

        // Sort artists by popularity (descending)
        const sortedArtists = artistsWithContractAddress.sort((a, b) => (b.popularity || 0) - (a.popularity || 0));
        setArtists(sortedArtists);
        setLoading(false);
      } catch (err) {
        console.error("Error fetching artist data:", err);
        if (err.response) {
          console.error("Response data:", err.response.data);
          console.error("Response status:", err.response.status);
          console.error("Response headers:", err.response.headers);
        } else if (err.request) {
          console.error("No response received:", err.request);
        } else {
          console.error("Error setting up request:", err.message);
        }
        setError("Failed to load artist data: " + (err.response?.data?.message || err.message));
        setLoading(false);
      }
    };

    fetchArtists();
  }, [factoryContract]);

  const handleSearch = async (searchTerm) => {
    if (!searchTerm.trim()) {
      setSearchResults([]);
      return;
    }

    try {
      console.log(`Searching artists with term: ${searchTerm}`);
      const response = await axios.get(
        `http://localhost:8080/api/artists/search?name=${encodeURIComponent(searchTerm)}`,
        {
          headers: {
            "Content-Type": "application/json",
            ...getAuthHeaders(),
          },
        }
      );
      console.log("Search results:", response.data);
      setSearchResults(response.data);
    } catch (err) {
      console.error("Error searching artists:", err);
      setSearchResults([]);
    }
  };

  const handleViewDetails = (artist) => {
    if (!artist.contractAddress) {
      console.warn("Missing contract address for artist:", artist);
      return;
    }
    navigate(`/artist-details/${artist.contractAddress}`, {
      state: { artistId: artist.artistId },
    });
  };

  // Map financials to artists
  const artistsWithFinancials = artists.map((artist, index) => ({
    ...artist,
    currentPrice: batchFinancials[index]?.currentPrice || "N/A",
    totalVolume: batchFinancials[index]?.volume24h || "N/A",
    marketCap: batchFinancials[index]?.marketCap || "N/A",
  }));

  if (loading || financialsLoading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>Loading artists...</p>
      </div>
    );
  }

  if (error) {
    return <div className="error-container">{error}</div>;
  }

  return (
    <div className="home-container">
      <h1>Welcome to Sounquity</h1>
      <p>Where fans and artists connect through share trading!</p>

      <div className={styles.searchWrapper}>
        <SearchBar onSearch={handleSearch} isModal={false} />
      </div>

      <div className="artist-list">
        {(searchResults.length > 0 ? searchResults : artistsWithFinancials).map((artist) => (
          <ArtistCard
            key={artist.artistId}
            artist={artist}
            onViewDetails={handleViewDetails}
          />
        ))}
      </div>

      <div className="feature-container">
        <h2>Discover & Trade Shares in Your Favorite Artists</h2>
        <p>Explore trending artists and be a part of their journey!</p>
      </div>

      <div className="footer">
        <p>Message from the backend: {message}</p>
      </div>
    </div>
  );
};

export default Home;