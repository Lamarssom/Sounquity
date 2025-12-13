export const getSpotifyToken = async () => {
    try {
        console.log("Fetching Spotify token...");

        const jwtToken = localStorage.getItem("jwtToken"); // Get stored JWT token
        if (!jwtToken) {
            throw new Error("JWT token missing. User might not be logged in.");
        }

        const response = await fetch("http://localhost:8080/api/spotify/token", {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${jwtToken}`, // Corrected this line
                "Content-Type": "application/json"
            },
        });

        console.log("Response status:", response.status);
        
        if (!response.ok) {
            throw new Error(`Failed to fetch Spotify token: ${response.status} ${response.statusText}`);
        }

        const data = await response.json();
        console.log("Token response:", data);

        if (!data.access_token) {
            throw new Error("Spotify token missing in response");
        }

        return data.access_token;
    } catch (error) {
        console.error("Error getting Spotify token:", error);
        return null;
    }
};