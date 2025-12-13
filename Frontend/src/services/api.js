const BASE_URL = "http://localhost:8080/api"; // Adjust if needed

// Helper function to get JWT token from local storage
const getAuthToken = () => {
  return localStorage.getItem("jwtToken");
};

// Caching user details
let cachedUserDetails = null;

// Fetch user details (with caching)
export const fetchUserDetails = async () => {
    if (cachedUserDetails) {
        return cachedUserDetails; // Return cached data if available
    }

    try {
        const jwtToken = getAuthToken();
        if (!jwtToken) {
            throw new Error("JWT token missing. User might not be logged in.");
        }

        const response = await fetch(`${BASE_URL}/users/details`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json",
            },
        });

        if (!response.ok) {
            throw new Error("Failed to fetch user details.");
        }

        const userDetails = await response.json();
        cachedUserDetails = userDetails; // Cache the user details
        return userDetails;
    } catch (error) {
        console.error("Error fetching user details:", error);
        return null;
    }
};

// Fetch Spotify access token (with retry logic)
export const getSpotifyToken = async () => {
    try {
        console.log("Fetching Spotify token...");

        const jwtToken = getAuthToken();
        if (!jwtToken) {
            throw new Error("JWT token missing. User might not be logged in.");
        }

        const response = await fetch(`${BASE_URL}/spotify/token`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Spotify token fetch failed: ${errorText}`);
        }

        const data = await response.json();

        if (!data.access_token) {
            throw new Error("Spotify token missing in response");
        }

        return data.access_token;
    } catch (error) {
        console.error("Error getting Spotify token:", error);
        return null;
    }
};

// Get artist contract address using Artist ID
export const getArtistContractAddress = async (artistId) => {
    console.log("Fetching contract address for Artist ID:", artistId);
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/artists/contract/${artistId}`, {  // ✅ FIXED
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("Failed to fetch artist contract address.");
        }

        const data = await response.text(); // backend returns a plain string
        console.log("Fetched contract address:", data);
        return data;
    } catch (error) {
        console.error("Error getting contract address:", error);
        return null;
    }
};

// Fetch artist price history
export const getArtistPriceHistory = async (contractAddress) => {
    console.log("Fetching price history for contract address:", contractAddress);
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/artists/${contractAddress}/price-history`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("Failed to fetch artist price history.");
        }

        const data = await response.json();
        console.log("Fetched artist price history:", data);
        return data;
    } catch (error) {
        console.error("Error fetching price history:", error);
        return null;
    }
};

// Fetch artist stats (price, volume, etc.)
export const getArtistStats = async (contractAddress) => {
    console.log("Fetching artist stats for contract address:", contractAddress);
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/artists/${contractAddress}/stats`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) {
            throw new Error("Failed to fetch artist stats.");
        }

        const data = await response.json();
        console.log("Fetched artist stats:", data);
        return data;
    } catch (error) {
        console.error("Error fetching artist stats:", error);
        return null;
    }
};

// Buy artist shares
export const buyArtistShares = async (contractAddress, amount) => {
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/shares/${contractAddress}/buy`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ amount })
        });

        if (!response.ok) {
            throw new Error("Failed to buy shares.");
        }

        return await response.json();
    } catch (error) {
        console.error("Error buying shares:", error);
        return null;
    }
};

// Sell artist shares
export const sellArtistShares = async (contractAddress, amount) => {
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/shares/${contractAddress}/sell`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ amount })
        });

        if (!response.ok) {
            throw new Error("Failed to sell shares.");
        }

        return await response.json();
    } catch (error) {
        console.error("Error selling shares:", error);
        return null;
    }
};

// Update artist market data
export const updateArtistMarketData = async (artistId, artistDetails) => {   // ✅ use artistId instead of contractAddress
    console.log("Updating artist market data with:", artistDetails);
    try {
        const jwtToken = getAuthToken();
        const payload = {
            artistId: artistDetails.artistId,
            artistName: artistDetails.artistName,
            spotifyUrl: artistDetails.spotifyUrl,
            imageUrl: artistDetails.imageUrl,
            followers: artistDetails.followers,
            popularity: artistDetails.popularity,
            contractAddress: artistDetails.contractAddress,   // ✅ contractAddress stays inside body
            currentPrice: parseFloat(artistDetails.price),
            priceChangePercent: artistDetails.priceChangePercent || 0,
            totalVolume: parseInt(artistDetails.volume),
            availableSupply: artistDetails.availableSupply || 0,
            airdropSupply: artistDetails.airdropSupply || 0,
        };

        const response = await fetch(`${BASE_URL}/artists/${artistId}`, {   // ✅ FIXED
            method: "PUT",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify(payload),
        });

        if (!response.ok) {
            throw new Error("Failed to update artist market data.");
        }

        const data = await response.json();
        console.log("Artist market data updated successfully", data);
        return data;
    } catch (error) {
        console.error("API error updating artist market data:", error);
        throw error;
    }
};

// Fetch artist by contract address
export const getArtistByContractAddress = async (contractAddress) => {
    console.log("Fetching artist by contract address:", contractAddress);
    try {
        const jwtToken = getAuthToken();
        const response = await fetch(`${BASE_URL}/artists/by-contract/${contractAddress}`, {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`,
                "Content-Type": "application/json"
            }
        });

        if (!response.ok) {
            console.log(`[getArtistByContractAddress] Non-OK response: ${response.status}`);
            // Handle 404 or empty response by returning an empty object
            if (response.status === 404 || response.status === 204) {
                return {};
            }
            const errorText = await response.text();
            throw new Error(`Failed to fetch artist by contract address: ${errorText}`);
        }

        // Check if response has content before parsing
        const text = await response.text();
        if (!text) {
            console.log("[getArtistByContractAddress] Empty response body, returning empty object");
            return {};
        }

        const data = JSON.parse(text);
        console.log("Fetched artist data:", data);
        return data || {};
    } catch (error) {
        console.error("Error fetching artist by contract address:", error);
        return {};
    }
};