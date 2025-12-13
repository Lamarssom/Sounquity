package com.musicinvestment.musicapp.dto;

import com.musicinvestment.musicapp.dto.UserSignUpRequest;
import lombok.Data;
import java.util.List;

@Data
public class UserSignUpRequest {
    private String username;
    private String email;
    private String password;
    private List<String> favoriteArtistIds; // Required for airdrop allocation

    // No need for explicit constructors, getters, and setters because of @Data annotation
}
