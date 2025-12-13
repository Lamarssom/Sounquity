package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.Financials;
import com.musicinvestment.musicapp.service.BlockchainSyncService;
import com.musicinvestment.musicapp.service.ContractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainController.class);

    private final BlockchainSyncService blockchainSyncService;
    private final ContractService contractService;
    private final CacheManager cacheManager;

    @Autowired
    public BlockchainController(BlockchainSyncService blockchainSyncService, 
                               ContractService contractService, 
                               CacheManager cacheManager) {
        this.blockchainSyncService = blockchainSyncService;
        this.contractService = contractService;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/current-price/{artistId}")
    public ResponseEntity<String> getCurrentPrice(@PathVariable String artistId) {
        try {
            String price = contractService.getCurrentPrice(artistId);
            return ResponseEntity.ok(price);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching current price for artist " + artistId + ": " + e.getMessage());
        }
    }

    @GetMapping("/total-volume/{artistId}")
    public ResponseEntity<String> getTotalVolumeTraded(@PathVariable String artistId) {
        try {
            String volume = contractService.getTotalVolumeTraded(artistId);
            return ResponseEntity.ok(volume);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching total volume for artist " + artistId + ": " + e.getMessage());
        }
    }

    @GetMapping("/financials/{artistId}")
    public ResponseEntity<Financials> getFinancials(@PathVariable String artistId) {
        try {
            Financials financials = blockchainSyncService.computeFinancials(artistId);
            return ResponseEntity.ok(financials);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new Financials(
                "N/A", // currentPrice
                "N/A", // volume24h
                "N/A", // marketCap
                0.0,   // dailyLiquidity
                100.0, // liquidityPercentage
                0L,    // availableSupply
                null,  // nextReset
                0.0
            ));
        }
    }

    @GetMapping("/financials/by-user/{userAddress}")
    public ResponseEntity<BigDecimal> getUserTradeVolume(@PathVariable String userAddress) {
        try {
            BigDecimal tradeVolume = blockchainSyncService.computeUserTradeVolume(userAddress);
            return ResponseEntity.ok(tradeVolume);
        } catch (Exception e) {
            logger.error("Error fetching trade volume for userAddress {}: {}", userAddress, e.getMessage(), e);
            return ResponseEntity.status(500).body(BigDecimal.ZERO);
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

    @PostMapping("/subscribe/{contractAddress}")
    public ResponseEntity<String> subscribeToContract(@PathVariable String contractAddress) {
        try {
            blockchainSyncService.subscribeToNewContract(contractAddress);
            return ResponseEntity.ok("Subscribed to contract " + contractAddress);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error subscribing to contract " + contractAddress + ": " + e.getMessage());
        }
    }

    @PostMapping("/clear-caches")
    public ResponseEntity<String> clearCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        });
        return ResponseEntity.ok("All caches cleared");
    }
}