import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { signUpUser } from "../services/api";
import { getSpotifyToken } from "../services/spotify";
import "../styles/Auth.css";
import "../styles/Signup.css";

const Signup = () => {
    const [formData, setFormData] = useState({
        username: "",
        email: "",
        password: "",
        favoriteArtistIds: [],
    });

    const [artists, setArtists] = useState([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [error, setError] = useState(null);
    const [successMessage, setSuccessMessage] = useState(null);
    const [spotifyToken, setSpotifyToken] = useState("");

    const navigate = useNavigate();

    useEffect(() => {
        const fetchToken = async () => {
            const token = await getSpotifyToken();
            if (token) {
                setSpotifyToken(token);
            } else {
                console.warn("Skipping artist search since Spotify token is missing.");
            }
        };
        fetchToken();
    }, []);

    const searchArtists = async (query) => {
        if (!query || !spotifyToken) return;
        try {
            const response = await fetch(
                `https://api.spotify.com/v1/search?q=${query}&type=artist&limit=5`,
                {
                    headers: { Authorization: `Bearer ${spotifyToken}` },
                }
            );

            const data = await response.json();
            if (data.artists && data.artists.items) {
                setArtists(data.artists.items);
            }
        } catch (err) {
            console.error("Error fetching artists:", err);
        }
    };

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleArtistSearch = (e) => {
        setSearchQuery(e.target.value);
        searchArtists(e.target.value);
    };

    const handleArtistSelection = (artistId) => {
        let updatedArtists = [...formData.favoriteArtistIds];

        if (updatedArtists.includes(artistId)) {
            updatedArtists = updatedArtists.filter((id) => id !== artistId);
        } else {
            if (updatedArtists.length < 2) {
                updatedArtists.push(artistId);
            } else {
                alert("You can only select 2 favorite artists.");
                return;
            }
        }

        setFormData({ ...formData, favoriteArtistIds: updatedArtists });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setSuccessMessage(null);

        if (formData.favoriteArtistIds.length !== 2) {
            setError("You must select exactly 2 favorite artists.");
            return;
        }

        try {
            console.log("Submitting signup form with:", formData);
            
            const response = await signUpUser(formData);
            console.log("Received response:", response);
            
            if (!response) {
                throw new Error("No response from server.");
            }
            
            const responseData = response;
            console.log("Parsed response data:", responseData);
            
            if (responseData) {
                setSuccessMessage(responseData.message || "Signup successful! Airdrop allocated.");
            
                // Store JWT token
                if (responseData.token) {
                    localStorage.setItem("jwtToken", responseData.token);
                    console.log("Stored JWT Token:", localStorage.getItem("jwtToken"));
                } else {
                    console.warn("JWT token not found in response");
                }
            
                // Redirect to login after 2 seconds
                setTimeout(() => {
                    navigate("/login");
                }, 2000);
            } else {
                throw new Error(responseData.error || "Signup failed.");
            }
        } catch (err) {
            setError("Signup error: " + err.message);
            console.error("Signup error:", err);
        }
    };

    return (
        <div className="auth-container">
            <h2>Sign Up</h2>
            {error && <p className="error">{error}</p>}
            {successMessage && <p className="success">{successMessage}</p>}

            <form onSubmit={handleSubmit} className="auth-form">
                <input type="text" name="username" placeholder="Username" value={formData.username} onChange={handleChange} required />
                <input type="email" name="email" placeholder="Email" value={formData.email} onChange={handleChange} required />
                <input type="password" name="password" placeholder="Password" value={formData.password} onChange={handleChange} required />

                <div className="artist-search">
                    <label>Search & Select 2 Favorite Artists:</label>
                    <input type="text" placeholder="Search for an artist..." value={searchQuery} onChange={handleArtistSearch} />

                    {/* Display artist search results */}
                    {artists.length > 0 && (
                        <div className="artist-results">
                            {artists.map((artist) => (
                                <div
                                    key={artist.id}
                                    className={`artist-card ${formData.favoriteArtistIds.includes(artist.id) ? "selected" : ""}`}
                                    onClick={() => handleArtistSelection(artist.id)}
                                >
                                    <img src={artist.images.length > 0 ? artist.images[0].url : "https://via.placeholder.com/50"} alt={artist.name} />
                                    <span>{artist.name}</span>
                                    {formData.favoriteArtistIds.includes(artist.id) && <span className="checkmark">✔</span>}
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                <button type="submit" className="auth-btn">Sign Up</button>
            </form>
        </div>
    );
};

export default Signup;