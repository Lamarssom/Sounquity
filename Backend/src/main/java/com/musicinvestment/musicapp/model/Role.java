package com.musicinvestment.musicapp.model;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    USER,
    ADMIN;

    @Override
    public String getAuthority() {
        return name(); // Returns "USER" or "ADMIN"
    }
}