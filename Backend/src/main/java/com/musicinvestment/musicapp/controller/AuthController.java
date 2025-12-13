package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.service.JwtService;
import com.musicinvestment.musicapp.service.UserService;
import com.musicinvestment.musicapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Value("${jwt.refresh.token.expiration:86400000}") // Set refresh token expiration to 1 day (default)
    private long refreshTokenExpirationTime;

    public AuthController(JwtService jwtService, JwtUtil jwtUtil, UserService userService) {
        this.jwtService = jwtService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    // Refresh the JWT token using the refresh token
    @PostMapping("/refresh-token")
    public String refreshToken(@RequestBody String refreshToken, HttpServletRequest request) {
        // Extract username from refresh token (or check its validity)
        String username = jwtUtil.extractUsername(refreshToken);

        // Here, you can also add checks to validate the refresh token (e.g., check it against a database or cache)

        // Generate a new access token
        String newToken = jwtService.generateToken(userService.loadUserByUsername(username));

        // Return the new token to the client
        return newToken;
    }
}