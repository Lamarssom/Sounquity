import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDisconnect, useSignMessage } from 'wagmi';
import axios from 'axios';
import Modal from 'react-modal';
import { getPublicWeb3 } from '../utilities/web3.js';
import { getAuthHeaders } from '../utilities/auth';
import ArtistSharesFactory from '../abis/ArtistSharesFactory.json';
import styles from '../styles/ConnectWallet.module.css';
import searchStyles from '../styles/SearchBar.module.css';
import { ToastContainer, toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { Spinner } from 'react-bootstrap';
import { jwtDecode } from 'jwt-decode';
import { useAppKitAccount } from '@reown/appkit/react';
import { FACTORY_CONTRACT_ADDRESS } from "../utilities/config.js";

Modal.setAppElement('#root');

const ConnectWallet = () => {
  const { address, isConnected } = useAppKitAccount();
  const { disconnect } = useDisconnect();
  const { signMessageAsync } = useSignMessage();
  const [error, setError] = useState(null);
  const [isArtistSelectionOpen, setIsArtistSelectionOpen] = useState(false);
  const [artists, setArtists] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedArtists, setSelectedArtists] = useState([]);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isAuthChecked, setIsAuthChecked] = useState(false); // New state to track auth validation completion
  const [isSigning, setIsSigning] = useState(false);
  const navigate = useNavigate();
  const searchContainerRef = useRef(null);
  const searchWrapperRef = useRef(null);
  const modalRef = useRef(null);

  const web3 = useMemo(() => getPublicWeb3(), []);
  const factoryAddress = FACTORY_CONTRACT_ADDRESS;
  const factoryContract = useMemo(() => {
    return new web3.eth.Contract(ArtistSharesFactory.abi, factoryAddress);
  }, [web3]);

  // Validate JWT token on mount
  useEffect(() => {
    const validateToken = async () => {
      const token = localStorage.getItem('jwtToken');
      if (token) {
        try {
          const decoded = jwtDecode(token);
          const currentTime = Math.floor(Date.now() / 1000);
          if (decoded.exp && decoded.exp < currentTime) {
            console.log('[ConnectWallet] Token expired, clearing storage');
            localStorage.removeItem('jwtToken');
            setIsAuthenticated(false);
            toast.info('Session expired. Please reconnect wallet.');
          } else {
            const response = await axios.get(
              `${import.meta.env.VITE_API_URL}/users/details`,
              { headers: { Authorization: `Bearer ${token}` } }
            );
            console.log('[ConnectWallet] Token valid, user details:', response.data);
            setIsAuthenticated(true);
            setSelectedArtists(response.data.data.favoriteArtists?.map(String) || []);
          }
        } catch (err) {
          console.error('[ConnectWallet] Token validation failed:', err);
          localStorage.removeItem('jwtToken');
          setIsAuthenticated(false);
          toast.error('Invalid session. Please reconnect wallet.');
        }
      }
      setIsAuthChecked(true); // Mark auth check as complete
    };
    validateToken();
  }, []);

  // Open artist selection modal after wallet connection, but only after auth check
  useEffect(() => {
    if (isAuthChecked && isConnected && !isAuthenticated && !isArtistSelectionOpen) {
      setIsArtistSelectionOpen(true);
    }
  }, [isAuthChecked, isConnected, isAuthenticated, isArtistSelectionOpen]);

  useEffect(() => {
    console.log('[ConnectWallet] Modal mounted');
    return () => console.log('[ConnectWallet] Modal unmounted');
  }, [isArtistSelectionOpen]);

  useEffect(() => {
    if (isArtistSelectionOpen && searchContainerRef.current && searchWrapperRef.current) {
      const searchContainers = document.querySelectorAll(`.${searchStyles.searchContainer}`);
      console.log('[ConnectWallet] Total searchContainer elements in DOM:', searchContainers.length);
      if (searchContainers.length > 1) {
        console.warn('[ConnectWallet] Multiple searchContainer elements detected.');
      }
    }
  }, [searchQuery, isArtistSelectionOpen]);

  const closeModal = () => {
    const confirmClose = window.confirm('Are you sure you want to cancel? You will be disconnected from your wallet.');
    if (confirmClose) {
      disconnect(); // Disconnect wallet to prevent modal from re-opening
      setIsArtistSelectionOpen(false);
      setSearchQuery('');
      setArtists([]);
      setError(null);
      console.log('[ConnectWallet] Modal closed, state reset, wallet disconnected');
      toast.info('Wallet connection canceled.');
    } // If not confirmed, modal stays open
  };

  const fetchArtists = async (query = '') => {
    try {
      setLoading(true);
      const url = query
        ? `${import.meta.env.VITE_API_URL}/artists/search?name=${encodeURIComponent(query)}`
        : `${import.meta.env.VITE_API_URL}/artists`;
      console.log('[ConnectWallet] Fetching artists from:', url);
      const response = await axios.get(url, { headers: getAuthHeaders() });
      const artistsWithContractAddress = await Promise.all(
        response.data.map(async (artist) => {
          try {
            if (!artist.artistId) {
              console.warn('[ConnectWallet] Missing artistId for artist:', artist);
              return { ...artist, contractAddress: null };
            }
            const contractAddress = await factoryContract.methods
              .getTokenByArtistId(artist.artistId)
              .call();
            return { ...artist, contractAddress };
          } catch (err) {
            console.error(`[ConnectWallet] Error fetching contract address for ${artist.artistName}:`, err);
            return { ...artist, contractAddress: null };
          }
        })
      );
      const validArtists = artistsWithContractAddress.filter((artist) => artist.artistId != null);
      setArtists(validArtists);
      setLoading(false);
    } catch (err) {
      console.error('[ConnectWallet] Error fetching artists:', err);
      setError('Failed to load artists. Please try again.');
      setArtists([]);
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isArtistSelectionOpen) {
      fetchArtists();
    }
  }, [isArtistSelectionOpen]);

  const handleSearch = () => {
    fetchArtists(searchQuery);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleArtistSelect = (artistId) => {
    const id = String(artistId);
    if (selectedArtists.includes(id)) {
      setSelectedArtists(selectedArtists.filter((selectedId) => selectedId !== id));
    } else if (selectedArtists.length < 5) {
      setSelectedArtists([...selectedArtists, id]);
    } else {
      setError('You can select up to 5 favorite artists.');
    }
  };

  const authenticateWallet = async () => {
    if (isConnected && selectedArtists.length >= 2 && !isAuthenticated) {
      setIsSigning(true);
      toast.info('Signing message to authenticate...');
      try {
        const timestamp = new Date().toISOString();
        const message = `Sign this message to authenticate with Sounquity at ${timestamp}`;
        console.log('[ConnectWallet] Requesting signature for message:', message);
        console.log('[ConnectWallet] Wallet address:', address);
        const signature = await signMessageAsync({ message });
        console.log('[ConnectWallet] Signature generated:', signature);

        const favoriteArtistIds = selectedArtists.join(',');
        const payload = {
          walletAddress: address,
          message,
          signature,
          favoriteArtistIds,
        };
        console.log('[ConnectWallet] Sending wallet-login request with payload:', payload);
        const response = await axios.post(
          `${import.meta.env.VITE_API_URL}/users/wallet-login`,
          payload,
          { headers: getAuthHeaders() }
        );
        console.log('[ConnectWallet] Wallet login response:', response.data);
        localStorage.setItem('jwtToken', response.data.data.token);
        console.log('[ConnectWallet] JWT token stored:', response.data.data.token);
        setIsAuthenticated(true);
        toast.success('Wallet connected successfully!');
        navigate('/');
        setIsArtistSelectionOpen(false);
      } catch (err) {
        console.error('[ConnectWallet] Authentication error:', err);
        setError(`Failed to connect wallet: ${err.response?.data?.message || err.message}`);
        toast.error('Authentication failed. Please try again.');
      } finally {
        setIsSigning(false);
      }
    } else if (isConnected && selectedArtists.length < 2) {
      setError('Please select at least 2 favorite artists.');
    }
  };

  const handleDisconnect = () => {
    disconnect();
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('@appkit/identity_cache'); // THIS IS THE KEY
    sessionStorage.clear();
    
    setSelectedArtists([]);
    setIsAuthenticated(false);
    setIsAdmin && setIsAdmin(false); // if you have this state
    
    toast.info('Wallet disconnected. See you soon!');
    navigate('/');
  };

  return (
    <div className={styles.walletContainer}>
      <ToastContainer position="top-right" autoClose={3000} hideProgressBar />
      {isConnected && isAuthenticated ? (
        <div className={styles.connected}>
          <span className={styles.account}>
            Wallet Connected: {address ? `${address.slice(0, 6)}...${address.slice(-4)}` : 'Unknown'}
          </span>
          <button onClick={handleDisconnect} className={styles.disconnectButton}>
            Disconnect
          </button>
        </div>
      ) : (
        <appkit-button />
      )}
      <Modal
        isOpen={isArtistSelectionOpen}
        onRequestClose={closeModal}
        className={styles.modal}
        overlayClassName={styles.overlay}
        key="artist-select-modal"
        ref={modalRef}
      >
        <h2 className={styles.modalTitle}>Select Favorite Artists</h2>
        <p className={styles.modalSubtitle}>Please select at least 2 favorite artists to sign in:</p>
        <div ref={searchWrapperRef}>
          <div className={searchStyles.searchContainer} ref={searchContainerRef}>
            <input
              type="text"
              placeholder="Search for an artist..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={handleKeyPress}
              className={searchStyles.searchInput}
            />
            <button
              onClick={handleSearch}
              className={searchStyles.searchButton}
            >
              Search
            </button>
          </div>
        </div>
        {loading ? (
          <p className={styles.loading}>Loading artists...</p>
        ) : artists.length > 0 ? (
          <ul className={styles.artistList}>
            {artists.map((artist) => (
              <li
                key={artist.artistId}
                className={`${styles.artistListItem} ${
                  selectedArtists.includes(String(artist.artistId)) ? styles.selected : ''
                }`}
                onClick={() => handleArtistSelect(artist.artistId)}
              >
                <div className={styles.artistListContent}>
                  {artist.imageUrl && (
                    <img
                      src={artist.imageUrl}
                      alt={artist.name || artist.artistName || 'Artist'}
                      className={styles.artistListImage}
                      onError={(e) => {
                        e.target.onerror = null;
                        e.target.src = '/default-artist.jpg';
                      }}
                    />
                  )}
                  <span>{artist.name || artist.artistName || 'Unknown Artist'}</span>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles.noResults}>No artists found. Try a different search.</p>
        )}
        {error && <p className={styles.error}>{error}</p>}
        <div className={styles.modalActions}>
          <button onClick={closeModal} className={styles.cancelButton}>
            Cancel
          </button>
          <button
            onClick={authenticateWallet}
            className={`${styles.connectButton} ${
              selectedArtists.length < 2 || isSigning ? styles.disabled : ''
            }`}
            disabled={selectedArtists.length < 2 || isSigning}
          >
            {isSigning ? 'Signing...' : 'Connect'}
          </button>
        </div>
        {isSigning && (
          <div className={styles.signingOverlay}>
            <Spinner animation="border" size="sm" />
            <span>Signing message to authenticate...</span>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ConnectWallet;