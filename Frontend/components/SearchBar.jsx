// src/components/SearchBar.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import styles from '../styles/SearchBar.module.css';

const SearchBar = ({ onSearch, isModal = false }) => { // Added isModal prop
  const [query, setQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const navigate = useNavigate();

  const jwtToken = localStorage.getItem('jwtToken');

  const handleSearch = async () => {
    if (!query.trim()) {
      setSearchResults([]);
      if (onSearch) onSearch(query);
      return;
    }

    try {
      const response = await axios.get(
        `http://localhost:8080/api/artists/search?name=${encodeURIComponent(query)}`,
        {
          headers: jwtToken ? { Authorization: `Bearer ${jwtToken}` } : {},
        }
      );
      console.log('Search results:', response.data);
      setSearchResults(response.data);
      if (onSearch) onSearch(query);
    } catch (error) {
      console.error('Error searching for artist:', error);
      setSearchResults([]);
      if (onSearch) onSearch(query);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const handleArtistClick = (artist) => {
    if (artist.contractAddress) {
      navigate(`/artist-details/${artist.contractAddress}`);
    } else {
      console.warn('No contract address for artist:', artist);
    }
  };

  return (
    <div className={styles.searchWrapper}>
      <div className={styles.searchContainer}>
        <input
          type="text"
          placeholder="Search for an artist..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyPress={handleKeyPress}
          className={styles.searchInput}
        />
        <button
          onClick={handleSearch}
          className={styles.searchButton}
        >
          Search
        </button>
      </div>
      {!isModal && searchResults.length > 0 && (  // Hide dropdown in modal
        <ul className={styles.artistList}>
          {searchResults.map((artist, index) => (
            <li
              key={artist.artistId || index}
              className={styles.artistListItem}
              onClick={() => handleArtistClick(artist)}
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
      )}
    </div>
  );
};

export default SearchBar;