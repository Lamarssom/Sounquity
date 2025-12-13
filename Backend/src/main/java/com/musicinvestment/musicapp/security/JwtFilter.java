package com.musicinvestment.musicapp.security;

import com.musicinvestment.musicapp.service.JwtService;
import com.musicinvestment.musicapp.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtService jwtService, UserService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        logger.info("Processing request: {} {}", method, uri);

        // Bypass JWT filter for public endpoints
        if (isPublicEndpoint(request)) {
            logger.info("Bypassing JWT filter for public endpoint: {} {}", method, uri);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("No Bearer token found for: {} {}", method, uri);
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String walletAddress;

        try {
            walletAddress = jwtService.extractUsername(token);
            if (walletAddress == null || walletAddress.isBlank()) {
                logger.error("JWT Extraction failed: No wallet address found in token");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        } catch (RuntimeException e) {
            logger.error("JWT Extraction failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(walletAddress);
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.info("Authenticated user: {}", walletAddress);
                } else {
                    logger.error("JWT Token is invalid or expired for: {}", walletAddress);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired or invalid");
                    return;
                }
            } catch (Exception e) {
                logger.error("Authentication failed for: {} - {}", walletAddress, e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI().replaceAll("/+$", "");
        String method = request.getMethod();

        logger.info("Checking if request is public: {} {}", method, uri);

        if (method.equals("OPTIONS")) {
            return true;
        }

        if (method.equals("GET")) {
            return uri.equals("/api/users/wallet-login") ||
                   uri.equals("/spotify/token") ||
                   uri.equals("/api/auth/refresh-token") ||
                   uri.equals("/api/artists") || // Explicitly include /api/artists
                   uri.startsWith("/api/artists/search") ||
                   uri.startsWith("/api/artists/by-contract") ||
                   uri.startsWith("/api/artists/spotify") ||
                   uri.startsWith("/api/messages") ||
                   uri.startsWith("/api/blockchain");
        }

        if (method.equals("POST")) {
            return uri.equals("/api/users/wallet-login") ||
                   uri.equals("/spotify/token") ||
                   uri.equals("/api/auth/refresh-token") ||
                   uri.startsWith("/api/messages");
        }

        if (method.equals("PUT")) {
            return uri.startsWith("/api/artists") || // Cover /api/artists/ and /api/artists/*/update-contract
                   uri.matches("^/api/artists/[^/]+/update-contract$");
        }

        if (uri.equals("/error")) {
            return true;
        }

        return false;
    }
}