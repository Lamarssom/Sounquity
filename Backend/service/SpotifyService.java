package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.Artist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class SpotifyService {

    private static final Logger logger = Logger.getLogger(SpotifyService.class.getName());

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spotify.client.id}")
    private String clientId;

    @Value("${spotify.client.secret}")
    private String clientSecret;

    private String accessToken;
    private long tokenExpiryTime = 0;

    public SpotifyService() {}

    public Artist getArtistById(String id) {
        logger.info("Fetching artist by ID: " + id);

        // Validate input ID
        if (id == null || id.trim().isEmpty()) {
            logger.warning("Invalid artist ID provided: " + id);
            return null;
        }

        // Check if it's a contract address
        if (id.startsWith("0x") && id.length() == 42) {
            logger.info("Detected contract address, skipping Spotify API call for ID: " + id);
            return null;
        }

        return fetchArtistFromSpotify(id);
    }

    private Artist fetchArtistFromSpotify(String id) {
        logger.info("Fetching artist from Spotify API: " + id);
        String url = "https://api.spotify.com/v1/artists/" + id;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getAccessToken());
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            logger.info("Spotify API response code: " + response.getStatusCode());
            logger.info("Response body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseArtistData(response.getBody(), id, null);
            } else {
                logger.severe("Error fetching artist, received status code: " + response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.severe("HTTP error fetching artist: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.severe("Error fetching artist: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Artist parseArtistData(Map<String, Object> data, String id, String contractAddress) {
        String name = (String) data.getOrDefault("name", "Unknown Artist " + id);
        String externalUrl = "";
        Map<String, Object> externalUrls = (Map<String, Object>) data.get("external_urls");
        if (externalUrls != null) {
            externalUrl = (String) externalUrls.getOrDefault("spotify", "");
        }

        int followers = 0;
        Map<String, Object> followersMap = (Map<String, Object>) data.get("followers");
        if (followersMap != null) {
            Number totalFollowers = (Number) followersMap.get("total");
            if (totalFollowers != null) {
                followers = totalFollowers.intValue();
            }
        }

        int popularity = (Number) data.getOrDefault("popularity", 0) != null ? ((Number) data.get("popularity")).intValue() : 0;
        String imageUrl = getImageUrl(data);
        String spotifyId = (String) data.getOrDefault("id", id);

        logger.info("Parsed artist data - ID: " + spotifyId + ", Name: " + name + ", Popularity: " + popularity);
        return new Artist(id, name, externalUrl, followers, popularity, imageUrl, contractAddress, spotifyId);
    }

    private String getImageUrl(Map<String, Object> data) {
        List<Map<String, Object>> images = (List<Map<String, Object>>) data.get("images");
        if (images != null && !images.isEmpty()) {
            return (String) images.get(0).getOrDefault("url", "");
        }
        return "";
    }

    public String getAccessToken() {
        long currentTime = System.currentTimeMillis();
        if (accessToken == null || currentTime >= tokenExpiryTime) {
            logger.info("Access token expired or not available. Refreshing...");
            refreshAccessToken();
        }
        return accessToken;
    }

    private void refreshAccessToken() {
        logger.info("Requesting new access token from Spotify");

        String url = "https://accounts.spotify.com/api/token";
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedCredentials);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<String> request = new HttpEntity<>("grant_type=client_credentials", headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                accessToken = (String) response.getBody().get("access_token");
                Number expiresIn = (Number) response.getBody().getOrDefault("expires_in", 3600);
                tokenExpiryTime = System.currentTimeMillis() + (expiresIn.intValue() * 1000);
                logger.info("New access token obtained, expires in " + expiresIn + " seconds.");
            } else {
                logger.severe("Failed to retrieve Spotify access token. Status: " + response.getStatusCode());
                throw new RuntimeException("Failed to retrieve Spotify access token.");
            }
        } catch (Exception e) {
            logger.severe("Error fetching Spotify token: " + e.getMessage());
            throw new RuntimeException("Failed to authenticate with Spotify.", e);
        }
    }

    public int calculateTrendingScore(String artistId) {
        try {
            Artist artist = getArtistById(artistId);
            if (artist == null) {
                logger.warning("Artist not found, returning default score 0 for ID: " + artistId);
                return 0;
            }
            int score = (artist.getFollowers() / 1000) + (artist.getPopularity() * 2);
            logger.info("Calculated trending score for artist ID " + artistId + ": " + score);
            return score;
        } catch (Exception e) {
            logger.warning("Failed to calculate trending score for artist ID " + artistId + ": " + e.getMessage());
            return 0;
        }
    }

    public List<Artist> searchArtistsByName(String name) {
        logger.info("Searching for artists by name: " + name);
        String encodedName;
        try {
            encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.severe("Failed to encode artist name: " + name);
            throw new RuntimeException("Failed to encode artist name.", e);
        }
        String url = "https://api.spotify.com/v1/search?q=" + encodedName + "&type=artist&limit=10";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + getAccessToken());
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, request, new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = response.getBody();
                List<Map<String, Object>> artistsData = (List<Map<String, Object>>) ((Map<String, Object>) data.get("artists")).get("items");
                return parseArtistsData(artistsData);
            } else {
                logger.severe("Error fetching artist search results, received status code: " + response.getStatusCode());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.severe("Error fetching artist search: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Artist> parseArtistsData(List<Map<String, Object>> artistsData) {
        List<Artist> artists = new ArrayList<>();
        for (Map<String, Object> artistData : artistsData) {
            String id = (String) artistData.getOrDefault("id", "");
            String name = (String) artistData.getOrDefault("name", "Unknown Artist " + id);
            String externalUrl = "";
            Map<String, Object> externalUrls = (Map<String, Object>) artistData.get("external_urls");
            if (externalUrls != null) {
                externalUrl = (String) externalUrls.getOrDefault("spotify", "");
            }
            int followers = 0;
            Map<String, Object> followersMap = (Map<String, Object>) artistData.get("followers");
            if (followersMap != null) {
                Number totalFollowers = (Number) followersMap.get("total");
                if (totalFollowers != null) {
                    followers = totalFollowers.intValue();
                }
            }
            int popularity = (Number) artistData.getOrDefault("popularity", 0) != null ? ((Number) artistData.get("popularity")).intValue() : 0;
            String imageUrl = getImageUrl(artistData);
            String spotifyId = (String) artistData.getOrDefault("id", id);

            artists.add(new Artist(id, name, externalUrl, followers, popularity, imageUrl, null, spotifyId));
        }
        return artists;
    }
}