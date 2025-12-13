package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.service.AirdropService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/airdrops")
public class AirdropController {

    private final AirdropService airdropService;

    @Autowired
    public AirdropController(AirdropService airdropService) {
        this.airdropService = airdropService;
    }

    @PostMapping("/allocate")
    public ResponseEntity<String> allocateAirdrop(@RequestBody Map<String, Object> requestData) {
        try {
            if (!requestData.containsKey("userId")) {
                return ResponseEntity.badRequest().body("Missing userId field.");
            }

            String userId = requestData.get("userId").toString();
            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid userId format.");
            }

            if (!requestData.containsKey("favoriteArtistIds") || !(requestData.get("favoriteArtistIds") instanceof List<?>)) {
                return ResponseEntity.badRequest().body("Invalid or missing favoriteArtistIds field.");
            }

            List<String> favoriteArtistIds = ((List<?>) requestData.get("favoriteArtistIds")).stream()
                    .map(Object::toString)
                    .toList();

            airdropService.allocateAirdrop(userId, favoriteArtistIds);

            return ResponseEntity.ok("Airdrop allocated successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error allocating airdrop: " + e.getMessage());
        }
    }
}