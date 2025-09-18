package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.Financials;
import com.musicinvestment.musicapp.service.BlockchainSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final BlockchainSyncService blockchainSyncService;

    @Autowired
    public BlockchainController(BlockchainSyncService blockchainSyncService) {
        this.blockchainSyncService = blockchainSyncService;
    }

    @GetMapping("/current-price/{artistId}")
    public ResponseEntity<String> getCurrentPrice(@PathVariable String artistId) {
        try {
            String price = blockchainSyncService.getCurrentPrice(artistId);
            return ResponseEntity.ok(price);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching current price for artist " + artistId + ": " + e.getMessage());
        }
    }

    @GetMapping("/total-volume/{artistId}")
    public ResponseEntity<String> getTotalVolumeTraded(@PathVariable String artistId) {
        try {
            String volume = blockchainSyncService.getTotalVolumeTraded(artistId);
            return ResponseEntity.ok(volume);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching total volume for artist " + artistId + ": " + e.getMessage());
        }
    }

    @PostMapping("/buy-shares/{artistId}")
    public ResponseEntity<String> buyShares(@PathVariable String artistId, 
                                           @RequestParam BigInteger amount, 
                                           @RequestParam(required = false) BigInteger weiValue) {
        try {
            if (weiValue == null || weiValue.compareTo(BigInteger.ZERO) <= 0) {
                throw new IllegalArgumentException("Invalid weiValue for buy operation");
            }
            blockchainSyncService.buyShares(artistId, amount, weiValue);
            return ResponseEntity.ok("Shares purchased successfully for artist " + artistId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error buying shares for artist " + artistId + ": " + e.getMessage());
        }
    }

    @PostMapping("/sell-shares/{artistId}")
    public ResponseEntity<String> sellShares(@PathVariable String artistId, 
                                            @RequestParam BigInteger amount) {
        try {
            blockchainSyncService.sellShares(artistId, amount);
            return ResponseEntity.ok("Shares sold successfully for artist " + artistId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error selling shares for artist " + artistId + ": " + e.getMessage());
        }
    }

    @GetMapping("/financials/{artistId}")
    public ResponseEntity<Financials> getFinancials(@PathVariable String artistId) {
        try {
            Financials financials = blockchainSyncService.computeFinancials(artistId);
            return ResponseEntity.ok(financials);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new Financials("N/A", "N/A", "N/A"));
        }
    }

    @GetMapping("/batch-financials")
    public ResponseEntity<List<Financials>> getBatchFinancials(@RequestParam List<String> artistIds) {
        try {
            List<Financials> financialsList = artistIds.stream()
                    .map(blockchainSyncService::computeFinancials)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(financialsList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(List.of());
        }
    }
}