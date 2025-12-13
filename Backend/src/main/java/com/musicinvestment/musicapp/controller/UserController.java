package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.service.UserService;
import com.musicinvestment.musicapp.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/wallet-login")
    public ResponseEntity<?> walletLogin(@RequestBody Map<String, String> request) {
        try {
            String walletAddress = request.get("walletAddress");
            String signature = request.get("signature");
            String message = request.get("message");
            List<String> favoriteArtistIds = request.containsKey("favoriteArtistIds") 
                ? Arrays.asList(request.get("favoriteArtistIds").split(",\\s*"))
                : null;

            // Validate inputs
            if (walletAddress == null || signature == null || message == null) {
                logger.warn("Missing required fields: walletAddress={}, signature={}, message={}", 
                            walletAddress, signature, message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", Map.of("code", "INVALID_REQUEST", "message", "Missing required fields")));
            }

            // Authenticate user and store favorite artists
            logger.info("Authenticating user with walletAddress: {}", walletAddress);
            User user = userService.loginUser(walletAddress, signature, message, favoriteArtistIds);
            if (user == null) {
                logger.error("UserService returned null for walletAddress: {}", walletAddress);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", Map.of("code", "SERVER_ERROR", "message", "User authentication failed")));
            }
            logger.info("User authenticated: id={}, balance={}", user.getId(), user.getBalance());

            // Generate JWT
            String token = jwtUtil.generateToken(user.getId());
            if (token == null) {
                logger.error("Failed to generate JWT for user: {}", user.getId());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", Map.of("code", "SERVER_ERROR", "message", "Token generation failed")));
            }
            logger.info("Generated JWT for user: {}", user.getId());

            // Handle potential null balance
            BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of(
                "token", token,
                "userId", user.getId(),
                "balance", balance
            ));
            response.put("error", null);
            logger.debug("Returning success response: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            logger.error("Authentication failed for walletAddress: {}, error: {}", 
                        request.get("walletAddress"), errorMessage, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("data", null);
            errorResponse.put("error", Map.of("code", "AUTH_FAILED", "message", errorMessage));
            logger.debug("Returning error response: {}", errorResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @GetMapping("/details")
    public ResponseEntity<?> getUserDetails(@RequestHeader("Authorization") String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            String walletAddress = jwtUtil.extractUsername(token);
            User user = userService.findUserById(walletAddress)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            logger.info("Fetched details for user: id={}, balance={}", user.getId(), user.getBalance());

            BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
            List<String> favoriteArtists = user.getFavoriteArtists() != null 
                ? user.getFavoriteArtists().stream().map(Artist::getId).collect(Collectors.toList())
                : Collections.emptyList();

            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of(
                "userId", user.getId(),
                "balance", balance,
                "favoriteArtists", favoriteArtists
            ));
            response.put("error", null);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Failed to fetch user details";
            logger.error("Failed to fetch user details: {}", errorMessage, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("data", null);
            errorResponse.put("error", Map.of("code", "UNAUTHORIZED", "message", errorMessage));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }
}