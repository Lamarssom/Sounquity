package com.musicinvestment.musicapp.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${JWT_SECRET}")
    private String secretKey;

    @Value("${jwt.expiration:14400000}")
    private long expirationTime;

    @Value("${jwt.refreshExpiration:2592000000}")
    private long refreshExpirationTime;

    @PostConstruct
    public void checkSecret() {
        System.out.println("JWT_SECRET in JwtUtil: " + secretKey);
    }

    public Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(String walletAddress) {
        String token = Jwts.builder()
                .subject(walletAddress)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey())
                .compact();

        System.out.println("Generated JWT token for wallet address: " + walletAddress);
        System.out.println("JWT token: " + token);
        
        return token;
    }

    public String generateRefreshToken(String walletAddress) {
        String token = Jwts.builder()
                .subject(walletAddress)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(getSigningKey())
                .compact();

        System.out.println("Generated Refresh JWT token for wallet address: " + walletAddress);
        System.out.println("Refresh JWT token: " + token);
        
        return token;
    }

    public String extractUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException e) {
            throw new JwtTokenException("Error extracting wallet address from token: " + e.getMessage(), e);
        }
    }

    public boolean validateToken(String token, String walletAddress) {
        try {
            final String extractedWalletAddress = extractUsername(token);
            System.out.println("Extracted wallet address from token: " + extractedWalletAddress);
            System.out.println("Comparing with wallet address: " + walletAddress);

            return extractedWalletAddress.equalsIgnoreCase(walletAddress) && !isTokenExpired(token);
        } catch (JwtTokenException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expirationDate = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
            
            boolean expired = expirationDate.before(new Date());

            System.out.println("Token expiration date: " + expirationDate);
            System.out.println("Is token expired: " + expired);
            
            return expired;
        } catch (JwtException e) {
            throw new JwtTokenException("Error checking token expiration: " + e.getMessage(), e);
        }
    }

    public String refreshToken(String refreshToken) {
        try {
            String walletAddress = extractUsername(refreshToken);
            return generateToken(walletAddress);
        } catch (JwtTokenException e) {
            throw new JwtTokenException("Error refreshing token: " + e.getMessage(), e);
        }
    }

    public static class JwtTokenException extends RuntimeException {
        public JwtTokenException(String message, Throwable cause) {
            super(message, cause);
        }

        public JwtTokenException(String message) {
            super(message);
        }
    }
}