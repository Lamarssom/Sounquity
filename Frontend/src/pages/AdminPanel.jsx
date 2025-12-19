// src/pages/AdminPanel.jsx
import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { toast } from "react-toastify";
import { createArtistTokenOnFactory } from "../utilities/blockchain";
import { getAuthHeaders } from "../utilities/auth";
import { useAppKitAccount } from '@reown/appkit/react';
import SearchBar from "../components/SearchBar";

const ADMIN_WALLETS = [
  "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
  "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
];

const generateSymbol = (name) => {
  if (!name) return "ARTIST";
  return name.replace(/[^a-zA-Z]/g, "").toUpperCase().substring(0, 7);
};

  const AdminPanel = () => {
    const { address, isConnected } = useAppKitAccount();
    const navigate = useNavigate();
    const [artists, setArtists] = useState([]);
    const [loading, setLoading] = useState(true);
    const hasChecked = useRef(false);

    const isAdmin = isConnected && address && ADMIN_WALLETS.includes(address.toLowerCase());

    useEffect(() => {
      if (hasChecked.current) return; // ← PREVENT RE-RUN ON REFRESH
      hasChecked.current = true;

      if (!isConnected) {
        toast.error("Please connect your wallet");
        navigate("/");
        return;
      }

      if (!isAdmin) {
        toast.error("Unauthorized. Admin access only.");
        navigate("/");
        return;
      }

      // Now safe to load artists
      const loadArtists = async () => {
        if (artists.length > 0) return;
        try {
          const res = await axios.get(`${import.meta.env.VITE_API_URL}/artists`, {
            headers: getAuthHeaders(),
          });
          setArtists(res.data);
        } catch (err) {
          toast.error("Failed to load artists");
        } finally {
          setLoading(false);
        }
      };

      loadArtists();
    }, [isConnected, isAdmin, navigate]);

    const handleDeploy = async (artist) => {
      const artistId = artist.artistId || artist.spotifyId || artist.id || artist.Id;
      
      if (!artistId) {
        console.error("No artist ID found!", artist);
        toast.error("Artist ID missing!");
        return;
      }

      const name = artist.artistName || artist.name;
      const symbol = generateSymbol(name);

      try {
        const address = await createArtistTokenOnFactory(artistId, name, symbol);

        const normalizedAddress = address.toLowerCase();
        await axios.put(
          `${import.meta.env.VITE_API_URL}/artists/${artistId}/contract`,
          { contractAddress: normalizedAddress },
          { headers: getAuthHeaders() }
        ).then(res => {
          console.log("SUCCESS: Contract saved to backend:", res.data);
          toast.success(`$${symbol} is now LIVE!`);
        }).catch(err => {
          console.error("FAILED TO SAVE CONTRACT TO BACKEND:", err.response?.data || err.message);
          toast.error("Deployed but failed to save contract to DB! Check console.");
        });

        // Update UI
        setArtists(prev =>
          prev.map(a => 
            (a.artistId || a.id || a.Id) === artistId 
              ? { ...a, contractAddress: address } 
              : a
          )
        );

        toast.success(`$${symbol} is now LIVE!`);
      } catch (err) {
        console.error("Deployment failed:", err.response || err);
        toast.error("Failed to save contract to backend");
      }
    };

  if (loading) {
    return (
      <div style={{
        height: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#111",
        color: "var(--sounquity-yellow)",
        fontSize: "2rem",
        flexDirection: "column",
        gap: "2rem"
      }}>
        <div className="loading-spinner"></div>
        <p>Loading Admin Panel...</p>
      </div>
    );
  }

  return (
    <div className="container py-5">
      <h1 className="text-center mb-4" style={{ color: "var(--sounquity-yellow)", fontSize: "3rem", fontWeight: "900" }}>
        ADMIN PANEL
      </h1>
      <p className="text-center mb-5" style={{ fontSize: "1.5rem", color: "#0f0" }}>
        Wallet: {address ? `${address.slice(0, 6)}...${address.slice(-4)}` : "Not connected"}
      </p>

      <div className="mb-5" style={{ maxWidth: "600px", margin: "0 auto" }}>
        <SearchBar 
          onSearch={() => {}} 
          isModal={false} 
          onDeploy={handleDeploy} 
        />
      </div>

      <div className="row row-cols-1 row-cols-md-3 g-4">
        {artists.map((artist) => {
          const isDeployed = artist.contractAddress &&
            artist.contractAddress !== "0x0000000000000000000000000000000000000000";

          return (
            <div key={artist.artistId} className="col">
              <div className="card h-100 card-glass text-white">
                <img
                  src={artist.imageUrl || "/default-artist.jpg"}
                  className="card-img-top"
                  alt={artist.artistName}
                  style={{ height: "200px", objectFit: "cover" }}
                />
                <div className="card-body d-flex flex-column">
                  <h5 className="card-title">{artist.artistName || artist.name}</h5>
                  <p className="card-text">
                    <strong>Symbol:</strong> ${generateSymbol(artist.artistName || artist.name)}
                  </p>
                  {isDeployed ? (
                    <button
                      className="btn btn-success mt-auto"
                      onClick={() => {
                        console.log("NAVIGATING WITH ARTIST:", artist); // ← you will see the real object
                        navigate(`/artist-details/${artist.contractAddress}`, {
                          state: { 
                            artistId: artist.artistId   // ← THIS IS THE ONLY FIELD THAT EXISTS
                          }
                        });
                      }}
                    >
                      LIVE
                    </button>
                  ) : (
                    <button
                      className="btn btn-gradient mt-auto"
                      onClick={() => handleDeploy(artist)}
                    >
                      Deploy Token
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default AdminPanel;