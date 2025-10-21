package com.musicinvestment.musicapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "financials", "prices", "volumes", "contractAddresses", "userFinancials"
        );
        logger.info("Initialized CacheManager with caches: {}", cacheManager.getCacheNames());
        return cacheManager;
    }
}
