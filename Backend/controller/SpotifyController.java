package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.service.SpotifyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/spotify")
public class SpotifyController {

    private final SpotifyService spotifyService;
    private static final Logger logger = Logger.getLogger(SpotifyController.class.getName());

    public SpotifyController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    // Endpoint to get artist details from Spotify
    @GetMapping("/artist/{id}")
    public ResponseEntity<Object> getArtist(@PathVariable String id) {
        try {
            logger.info("Fetching artist details for ID: " + id);
            return ResponseEntity.ok(successResponse("artist", spotifyService.getArtistById(id)));
        } catch (RuntimeException e) {
            logger.severe("Error retrieving artist data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Failed to retrieve artist data", e.getMessage()));
        }
    }

    // Endpoint to Get Spotify Token
    @GetMapping("/token")
    public ResponseEntity<Object> getSpotifyToken() {
        try {
            logger.info("Fetching Spotify access token");
            String token = spotifyService.getAccessToken();  // This should now refresh the token if expired
            return ResponseEntity.ok(successResponse("access_token", token));
        } catch (RuntimeException e) {
            logger.severe("Error retrieving Spotify token: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Failed to retrieve Spotify token", e.getMessage()));
        }
    }

    // Success response wrapper
    private Map<String, Object> successResponse(String key, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put(key, data);
        return response;
    }

    // Error response wrapper
    private Map<String, Object> errorResponse(String message, String details) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("details", details);
        return response;
    }
}