package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @Column(length = 255)
    private String id; // Wallet address (e.g., 0x70997970C51812dc3A010C7d01b50e0d17dc79C8)

    @ManyToMany
    @JoinTable(
        name = "user_favorite_artists",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> favoriteArtists = new HashSet<>();

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO; // Default balance is 0.00

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Default role for all wallet-authenticated users
        return Set.of(() -> "ROLE_USER");
    }

    @Override
    public String getPassword() {
        return null; // No password for wallet-based auth
    }

    @Override
    public String getUsername() {
        return id; // Use wallet address as username
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}