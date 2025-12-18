package com.musicinvestment.musicapp.config;

import com.musicinvestment.musicapp.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .anonymous(anonymous -> anonymous.principal("anonymousUser").authorities("ROLE_ANONYMOUS"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/users/wallet-login", "/spotify/token", "/api/auth/refresh-token").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/artists").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/artists/by-contract/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/artists/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/artists/spotify").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/artists/candleData/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/artists/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/artists/*/update-contract").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/messages/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/messages/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/blockchain/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/users/details").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    // Allow public endpoints to proceed without rejecting anonymous access
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
                    response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "*");
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + "\"}");
                })
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "https://sounquity.vercel.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}