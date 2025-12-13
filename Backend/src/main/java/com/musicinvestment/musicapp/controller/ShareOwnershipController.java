package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.ShareOwnership;
import com.musicinvestment.musicapp.service.ShareOwnershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shares")
public class ShareOwnershipController {

    @Autowired
    private ShareOwnershipService shareOwnershipService;

    @PostMapping("/allocate")
    public ResponseEntity<String> allocateShares(
            @RequestParam String userId,
            @RequestParam String artistId,
            @RequestParam int shares) {
        
        if (userId == null || artistId == null || shares <= 0) {
            return ResponseEntity.badRequest().body("Invalid allocation request.");
        }

        shareOwnershipService.allocateShares(userId, artistId, shares);
        return ResponseEntity.ok("Shares allocated successfully!");
    }

    @GetMapping("/owned")
    public ResponseEntity<Integer> getSharesOwned(
            @RequestParam String userId,
            @RequestParam String artistId) {
        
        if (userId == null || artistId == null) {
            return ResponseEntity.badRequest().body(0);
        }

        int shares = shareOwnershipService.getSharesOwned(userId, artistId);
        return ResponseEntity.ok(shares);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ShareOwnership>> getUserShares(@PathVariable String userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<ShareOwnership> shares = shareOwnershipService.getUserShares(userId);
        return ResponseEntity.ok(shares);
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transferShares(
            @RequestParam String senderId,
            @RequestParam String receiverId,
            @RequestParam String artistId,
            @RequestParam int shares) {
        
        if (senderId == null || receiverId == null || artistId == null || shares <= 0) {
            return ResponseEntity.badRequest().body("Invalid transfer request.");
        }

        String response = shareOwnershipService.transferShares(senderId, receiverId, artistId, shares);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sell")
    public ResponseEntity<String> sellShares(
            @RequestParam String sellerId,
            @RequestParam String artistId,
            @RequestParam int shares,
            @RequestParam String buyerId) {

        if (sellerId == null || buyerId == null || artistId == null || shares <= 0) {
            return ResponseEntity.badRequest().body("Invalid sell request.");
        }

        String response = shareOwnershipService.sellShares(sellerId, artistId, shares, buyerId);
        return ResponseEntity.ok(response);
    }
}
