package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    private final JwtUtil jwtUtil;

    public JwtService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        String walletAddress = userDetails.getUsername(); // Wallet address
        String token = jwtUtil.generateToken(walletAddress);

        System.out.println("Generated JWT token for wallet address: " + walletAddress);
        System.out.println("JWT token payload: " + extractAllClaims(token));

        return token;
    }

    public String generateRefreshToken(UserDetails userDetails) {
        String walletAddress = userDetails.getUsername();
        String refreshToken = jwtUtil.generateRefreshToken(walletAddress);

        System.out.println("Generated refresh JWT token for wallet address: " + walletAddress);
        System.out.println("Refresh JWT token: " + refreshToken);

        return refreshToken;
    }

    public String refreshAccessToken(String refreshToken) {
        return jwtUtil.refreshToken(refreshToken);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String walletAddress = extractUsername(token);
        String userDetailsWalletAddress = userDetails.getUsername();

        System.out.println("Extracted wallet address: " + walletAddress);
        System.out.println("User details wallet address: " + userDetailsWalletAddress);
        System.out.println("Token expired: " + isTokenExpired(token));

        return (walletAddress.equalsIgnoreCase(userDetailsWalletAddress) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (JwtException e) {
            System.err.println("Token has expired or is invalid: " + e.getMessage());
            return true;
        }
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) jwtUtil.getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            System.err.println("Error parsing token: " + e.getMessage());
            throw new RuntimeException("Error parsing token: " + e.getMessage(), e);
        }
    }
}