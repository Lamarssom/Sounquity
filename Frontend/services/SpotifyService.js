import axios from "axios";
import { getAuthHeaders } from "../utilities/auth";

const SpotifyService = {
  cache: new Map(),

  /** Search artists by name (home page cards) */
  searchArtist: async (name) => {
    const isDev = process.env.NODE_ENV === "development";
    const log = (...args) => isDev && console.log(...args);
    try {
      log(`[SpotifyService] Searching for artist: ${name}`);
      const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/artists/search`, {
        params: { name },
        headers: getAuthHeaders(),
      });
      log("[SpotifyService] Artist search response:", response.data);
      return response.data;
    } catch (error) {
      console.error("[SpotifyService] Error searching artist:", error.response?.data || error.message);
      throw error;
    }
  },

  /** Fetch artist details from backend by Spotify artistId */
  getArtistDetailsFromBackend: async (artistId) => {
    const isDev = process.env.NODE_ENV === "development";
    const log = (...args) => isDev && console.log(...args);
    try {
      log(`[SpotifyService] Fetching artist details from backend for artistId: ${artistId}`);
      const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/artists/${artistId}`, {
        headers: getAuthHeaders(),
      });
      log("[SpotifyService] Artist details from backend:", response.data);
      return response.data;
    } catch (error) {
      console.error("[SpotifyService] Error fetching artist details:", error.response?.data || error.message);
      throw error;
    }
  },

  /** Get chart data for an artist */
  getArtistChartData: async (artistId) => {
    const isDev = process.env.NODE_ENV === "development";
    const log = (...args) => isDev && console.log(...args);
    try {
      log(`[SpotifyService] Fetching chart data for artistId: ${artistId}`);
      const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/artists/${artistId}/chart`, {
        headers: getAuthHeaders(),
      });
      log("[SpotifyService] Chart data response:", response.data);
      return response.data;
    } catch (error) {
      console.error("[SpotifyService] Error fetching chart data:", error.response?.data || error.message);
      throw error;
    }
  },

  /** Fetch artist details directly from Spotify through backend proxy */
  getArtistDetailsFromSpotify: async (artistId, retries = 3) => {
    const isDev = process.env.NODE_ENV === "development";
    const log = (...args) => isDev && console.log(...args);
    try {
      // Check cache first
      if (SpotifyService.cache.has(artistId)) {
        log(`[SpotifyService] Cache hit for artist: ${artistId}`);
        return SpotifyService.cache.get(artistId);
      }

      log(`[SpotifyService] Fetching artist details from Spotify for artistId: ${artistId}`);
      let lastError = null;
      for (let attempt = 0; attempt < retries; attempt++) {
        try {
          const response = await axios.get(
            `${import.meta.env.VITE_API_URL}/api/artists/spotify?link=https://open.spotify.com/artist/${artistId}`
          ); // No headers needed for public endpoint
          if (!response.data || Object.keys(response.data).length === 0) {
            throw new Error("Empty response from Spotify API");
          }
          log("[SpotifyService] Artist details from Spotify:", response.data);

          // Cache the response for 5 minutes
          SpotifyService.cache.set(artistId, response.data);
          setTimeout(() => SpotifyService.cache.delete(artistId), 300 * 1000);

          return response.data;
        } catch (error) {
          lastError = error;
          log(`[SpotifyService] Attempt ${attempt + 1} failed:, error.response?.data || error.message`);
          if (attempt < retries - 1) {
            await new Promise((resolve) => setTimeout(resolve, 2000 * (attempt + 1))); // Increased delay
          }
        }
      }
      throw lastError || new Error("Failed to fetch artist details after retries");
    } catch (error) {
      console.error("[SpotifyService] Error fetching Spotify data:", error.response?.data || error.message);
      throw error;
    }
  },

  /** Fetch artist by contract address */
  getArtistByContractAddress: async (contractAddress) => {
    const isDev = process.env.NODE_ENV === "development";
    const log = (...args) => isDev && console.log(...args);
    try {
      log(`[SpotifyService] Fetching artist by contract: ${contractAddress}`);
      const response = await axios.get(`${import.meta.env.VITE_API_URL}/api/artists/by-contract/${contractAddress}`, {
        headers: getAuthHeaders(),
      });
      log("[SpotifyService] Artist by contract response:", response.data);
      return response.data;
    } catch (error) {
      console.error("[SpotifyService] Error fetching artist by contract:", error.response?.data || error.message);
      throw error;
    }
  },
};

export default SpotifyService;