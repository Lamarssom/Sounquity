package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.model.Trade;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import com.musicinvestment.musicapp.repository.ChatMessageRepository;
import com.musicinvestment.musicapp.repository.TradeRepository;
import com.musicinvestment.musicapp.model.Financials;
import com.musicinvestment.musicapp.model.Artist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.utils.Numeric;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.repository.CandleDataRepository;
import com.musicinvestment.musicapp.service.CandleDataService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class BlockchainSyncService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainSyncService.class);
    private static final Pattern ETHEREUM_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal WEI_TO_ETH = new BigDecimal("1000000000000000000"); // 10^18
    private static final BigDecimal ETH_USD_SCALE = new BigDecimal("100000000");
    private static final BigInteger FALLBACK_ETH_USD_PRICE = BigInteger.valueOf(3500).multiply(BigInteger.TEN.pow(8));
    private static final long BLOCKS_PER_DAY = 5760; // Approx. 15s block time, 24h = 86400s / 15
    private static final long BACKFILL_DAYS = 30; // Backfill last 30 days

    private final Web3j web3j;
    private final ArtistSharesFactory artistSharesFactory;
    private final ArtistRepository artistRepository;
    private final TradeRepository tradeRepository;
    private final String privateKey;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;
    private final ContractService contractService;
    private final Set<String> subscribedContracts = ConcurrentHashMap.newKeySet();
    private final ExecutorService backfillExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> processedTxHashes = Collections.synchronizedSet(new HashSet<>());
    private final CandleDataService candleDataService;
    private final CandleDataRepository candleDataRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Autowired
    public BlockchainSyncService(
            Web3j web3j,
            ArtistSharesFactory artistSharesFactory,
            @Value("${web3j.private-key}") String privateKey,
            ArtistRepository artistRepository,
            TradeRepository tradeRepository,
            SimpMessagingTemplate messagingTemplate,
            CacheManager cacheManager,
            ContractService contractService,
            CandleDataService candleDataService,
            CandleDataRepository candleDataRepository,
            ChatMessageRepository chatMessageRepository ) {
        this.web3j = web3j;
        this.artistSharesFactory = artistSharesFactory;
        this.artistRepository = artistRepository;
        this.tradeRepository = tradeRepository;
        this.privateKey = privateKey;
        this.messagingTemplate = messagingTemplate;
        this.cacheManager = cacheManager;
        this.contractService = contractService;
        this.candleDataService = candleDataService;
        this.candleDataRepository = candleDataRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing BlockchainSyncService, devMode: {}", devMode);
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("Cleared cache: {}", cacheName);
            }
        });
        if (devMode) {
            tradeRepository.deleteAll();
            candleDataRepository.deleteAll();
            chatMessageRepository.deleteAllInBatch();
            processedTxHashes.clear();
            logger.info("Dev mode: Cleared trades, candles, and processed txHashes for clean start.");

            artistRepository.clearAllContractAddresses();
            logger.info("Dev mode: Cleared all artist contract addresses from DB.");
        }
        syncArtistContracts();
        if (!devMode) {
            try {
                backfillExecutor.submit(this::backfillHistoricalTrades).get(60, TimeUnit.SECONDS);
                logger.info("Historical trades backfill completed");
                backfillHistoricalCandles();
                logger.info("Historical candles backfill completed");
            } catch (Exception e) {
                logger.error("Backfill failed: {}", e.getMessage(), e);
            }
        } else {
            logger.info("Dev mode: Skipping backfill for clean testing.");
        }
        subscribeToTradeEvents();
    }

    private void backfillHistoricalCandles() {
        List<String> artistIds = artistRepository.findAll().stream()
                .map(Artist::getId)
                .collect(Collectors.toList());
        for (String artistId : artistIds) {
            aggregateHistoricalCandles(artistId);
        }
    }

    private void aggregateHistoricalCandles(String artistId) {
        List<Trade> trades = tradeRepository.findByArtistIdOrderByTimestampAsc(artistId);
        if (trades.isEmpty()) {
            logger.info("No trades to aggregate candles for artistId: {}", artistId);
            return;
        }

        for (Timeframe tf : Timeframe.values()) {
            if (!candleDataService.getCandleDataByArtistIdAndTimeframe(artistId, tf).isEmpty()) {
                logger.info("Candles already aggregated for {} - {}", artistId, tf.getValue());
                continue;
            }

            long interval = tf.getIntervalSeconds();
            LocalDateTime currentPeriod = null;
            CandleData currentCandle = null;
            LocalDateTime cutoff = LocalDateTime.now(ZoneId.systemDefault()).minusDays(30);

            for (Trade trade : trades) {
                LocalDateTime ts = trade.getTimestamp();
                if (ts.isBefore(cutoff)) {
                    continue;
                }

                // With this (average price = amountInUsd / amountTokens)
                BigDecimal amountTokens = new BigDecimal(trade.getAmount());
                BigDecimal amountInUsd = trade.getAmountInUsd();
                BigDecimal avgPriceUsd = amountTokens.compareTo(BigDecimal.ZERO) > 0 
                    ? amountInUsd.divide(amountTokens, 10, RoundingMode.HALF_UP)
                    : trade.getPriceInUsd(); // fallback

                BigDecimal priceUsd = avgPriceUsd;
                BigDecimal volumeTokens = new BigDecimal(trade.getAmount()); // Amount is already scaled
                String eventType = trade.getEventType().name();

                long epoch = ts.atZone(ZoneId.of("UTC")).toEpochSecond();
                long periodStartEpoch = (epoch / interval) * interval;
                LocalDateTime period = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodStartEpoch), ZoneId.of("UTC"));

                if (currentCandle == null || !period.equals(currentPeriod)) {
                    if (currentCandle != null) {
                        candleDataService.saveCandleData(currentCandle);
                    }
                    currentPeriod = period;
                    currentCandle = new CandleData(
                        artistId, tf, period,
                        priceUsd,
                        priceUsd,
                        priceUsd,
                        priceUsd,
                        volumeTokens
                    );
                    currentCandle.setLastEventType(Trade.EventType.valueOf(eventType));
                    logger.debug("Creating historical candle for {} at {}: OHLC=[{}-{}-{}-{}], vol={}",
                        tf.getValue(), period,
                        priceUsd.stripTrailingZeros().toPlainString(), // Increased scale for small values
                        priceUsd.stripTrailingZeros().toPlainString(),
                        priceUsd.stripTrailingZeros().toPlainString(),
                        priceUsd.stripTrailingZeros().toPlainString(),
                        volumeTokens.setScale(6, RoundingMode.HALF_UP));
                } else {
                    currentCandle.setHigh(currentCandle.getHigh().max(priceUsd));
                    currentCandle.setLow(currentCandle.getLow().min(priceUsd));
                    currentCandle.setClose(priceUsd);
                    currentCandle.setVolume(currentCandle.getVolume().add(volumeTokens));
                    currentCandle.setLastEventType(Trade.EventType.valueOf(eventType));
                    logger.debug("Updating historical candle for {} at {}: H={}, L={}, C={}, V={}",
                        tf.getValue(), period,
                        currentCandle.getHigh().setScale(8, RoundingMode.HALF_UP),
                        currentCandle.getLow().setScale(8, RoundingMode.HALF_UP),
                        priceUsd.stripTrailingZeros().toPlainString(),
                        currentCandle.getVolume().setScale(6, RoundingMode.HALF_UP));
                }
            }
            if (currentCandle != null) {
                candleDataService.saveCandleData(currentCandle);
            }
            logger.info("Aggregated historical candles for {} - {}", artistId, tf.getValue());
        }
    }

    private void syncArtistContracts() {
        try {
            logger.info("Syncing artist contracts from ArtistSharesFactory");
            List<String> deployedTokens = artistSharesFactory.getDeployedTokens().send();
            logger.debug("Deployed tokens: {}", deployedTokens);
            for (String contractAddress : deployedTokens) {
                if (contractAddress == null || contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
                    logger.debug("Skipping sync for invalid contractAddress: {}", contractAddress);
                    continue;
                }
                if (isValidEthereumAddress(contractAddress)) {
                    List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
                    if (artistIds.isEmpty()) {
                        String fetchedArtistId = artistSharesFactory.getTokenByArtistId(contractAddress).send();
                        if (fetchedArtistId != null && !fetchedArtistId.isEmpty()) {
                            artistRepository.updateContractAddress(fetchedArtistId, contractAddress);
                            logger.info("Synced contract {} for artistId {}", contractAddress, fetchedArtistId);
                        } else {
                            logger.warn("No artistId found for contractAddress {} in ArtistSharesFactory", contractAddress);
                        }
                    } else {
                        logger.info("Contract {} already mapped to artistIds: {}", contractAddress, artistIds);
                        if (artistIds.size() > 1) {
                            logger.warn("Multiple artist IDs found for contract {}: {}", contractAddress,
                                artistIds.stream().collect(Collectors.joining(", ")));
                        }
                    }
                } else {
                    logger.warn("Invalid Ethereum address format: {}", contractAddress);
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing artist contracts: {}", e.getMessage(), e);
        }
    }

    private void subscribeToTradeEvents() {
        List<String> contractAddresses = artistRepository.findAllContractAddresses();
        logger.info("Subscribing to trade events for contracts: {}", contractAddresses);
        for (String contractAddress : contractAddresses) {
            if (contractAddress == null || contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
                logger.debug("Skipping subscription for invalid contractAddress: {}", contractAddress);
                continue;
            }
            if (!isValidEthereumAddress(contractAddress)) {
                logger.warn("Invalid Ethereum address format: {}", contractAddress);
                continue;
            }

            String normalized = contractAddress.toLowerCase();
            if (subscribedContracts.contains(normalized)) {
                logger.info("Already subscribed to contract: {}", contractAddress);
                continue;
            }
            try {
                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                subscribeToSharesBoughtEvents(token, contractAddress);
                subscribeToSharesSoldEvents(token, contractAddress);
                subscribeToDailySellLimitUpdatedEvents(token, contractAddress);
                subscribeToCurveCompletedEvents(token, contractAddress);
                subscribedContracts.add(normalized);
            } catch (Exception e) {
                logger.error("Failed to subscribe to trade events for contract {}: {}", contractAddress, e.getMessage(), e);
            }
        }
    }

    public void subscribeToNewContract(String contractAddress) {
        if (!isValidEthereumAddress(contractAddress)) {
            logger.warn("Cannot subscribe to invalid contract address: {}", contractAddress);
            return;
        }

        String normalized = contractAddress.toLowerCase();

        if (subscribedContracts.contains(normalized)) {
            logger.info("Already subscribed to new contract: {}", contractAddress);
            return;
        }

        try {
            ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
            subscribeToSharesBoughtEvents(token, contractAddress);
            subscribeToSharesSoldEvents(token, contractAddress);
            subscribeToDailySellLimitUpdatedEvents(token, contractAddress);
            subscribeToCurveCompletedEvents(token, contractAddress);
            subscribedContracts.add(normalized);
            logger.info("Subscribed to events for new contract: {}", contractAddress);
        } catch (Exception e) {
            logger.error("Failed to subscribe to new contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private void subscribeToSharesBoughtEvents(ArtistSharesToken token, String contractAddress) {
        String normalizedContract = contractAddress.toLowerCase();
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(normalizedContract);
        if (artistIds.isEmpty()) {
            logger.warn("No artistId found for contractAddress {}", contractAddress);
            return;
        }
        String artistId = artistIds.get(0);
        if (artistIds.size() > 1) {
            logger.warn("Multiple artist IDs found for contract {}: {}", contractAddress,
                artistIds.stream().collect(Collectors.joining(", ")));
        }

        try {
            token.sharesBoughtEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                    .subscribe(event -> {
                        String txHash = event.log.getTransactionHash();
                        if (processedTxHashes.contains(txHash)) {
                            logger.info("Skipping already processed BUY event for txHash: {}", txHash);
                            return;
                        }
                        if (tradeRepository.findByTxHash(txHash).isPresent()) {
                            logger.info("Skipping duplicate BUY trade for txHash: {}", txHash);
                            processedTxHashes.add(txHash);
                            return;
                        }
                        processedTxHashes.add(txHash);
                        Trade trade = createTradeFromBuyEvent(event, artistId, contractAddress, getEthUsdPrice(token, contractAddress));
                        tradeRepository.save(trade);
                        logger.info("Saved SharesBought event for artistId {}: amount={}, price={}, txHash={}, amountInUsd={}, priceInUsd={}",
                            artistId, trade.getAmount(), trade.getPrice(), trade.getTxHash(), trade.getAmountInUsd(), trade.getPriceInUsd());

                        evictAllFinancialCaches();
                        tradeRepository.save(trade);  // ← YES, KEEP THIS "DUPLICATE"
                        evictUserTradeVolumeCache(trade.getBuyerOrSeller());

                        messagingTemplate.convertAndSend("/topic/trades/" + artistId, trade);

                        Financials updatedFinancials = computeFinancialsUncached(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);

                        updateCandlesForTrade(trade);
                    }, error -> logger.error("Error in SharesBought subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to SharesBought events for contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private void subscribeToSharesSoldEvents(ArtistSharesToken token, String contractAddress) {
        String normalizedContract = contractAddress.toLowerCase();
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(normalizedContract);
        if (artistIds.isEmpty()) {
            logger.warn("No artistId found for contractAddress {}", contractAddress);
            return;
        }
        String artistId = artistIds.get(0);
        if (artistIds.size() > 1) {
            logger.warn("Multiple artist IDs found for contract {}: {}", contractAddress,
                artistIds.stream().collect(Collectors.joining(", ")));
        }

        try {
            token.sharesSoldEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                    .subscribe(event -> {
                        String txHash = event.log.getTransactionHash();
                        if (processedTxHashes.contains(txHash)) {
                            logger.info("Skipping already processed SELL event for txHash: {}", txHash);
                            return;
                        }
                        if (tradeRepository.findByTxHash(txHash).isPresent()) {
                            logger.info("Skipping duplicate SELL trade for txHash: {}", txHash);
                            processedTxHashes.add(txHash);
                            return;
                        }
                        processedTxHashes.add(txHash);

                        // ← THIS LINE WAS MISSING!
                        Trade trade = createTradeFromSellEvent(event, artistId, contractAddress, getEthUsdPrice(token, contractAddress));

                        tradeRepository.save(trade);
                        logger.info("Saved SharesBought event for artistId {}: amount={}, price={}, txHash={}, amountInUsd={}, priceInUsd={}",
                            artistId, trade.getAmount(), trade.getPrice(), trade.getTxHash(), trade.getAmountInUsd(), trade.getPriceInUsd());

                        evictAllFinancialCaches();
                        tradeRepository.save(trade);  // ← YES, KEEP THIS "DUPLICATE"
                        evictUserTradeVolumeCache(trade.getBuyerOrSeller());

                        messagingTemplate.convertAndSend("/topic/trades/" + artistId, trade);

                        Financials updatedFinancials = computeFinancialsUncached(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);

                        updateCandlesForTrade(trade);
                    }, error -> logger.error("Error in SharesSold subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to SharesSold events for contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private void subscribeToDailySellLimitUpdatedEvents(ArtistSharesToken token, String contractAddress) {
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
        if (artistIds.isEmpty()) {
            logger.warn("No artistId found for contractAddress {}", contractAddress);
            return;
        }
        String artistId = artistIds.get(0);

        try {
            token.dailySellLimitUpdatedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                    .subscribe(event -> {
                        logger.info("DailySellLimitUpdated for artistId {}: newLimitUsd={}, timestamp={}",
                            artistId, event.newLimitUsd, event.timestamp);
                        evictAllFinancialCaches();
                        Financials updatedFinancials = computeFinancials(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                    }, error -> logger.error("Error in DailySellLimitUpdated subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to DailySellLimitUpdated events for contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private void subscribeToCurveCompletedEvents(ArtistSharesToken token, String contractAddress) {
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
        if (artistIds.isEmpty()) {
            logger.warn("No artistId found for contractAddress {}", contractAddress);
            return;
        }
        String artistId = artistIds.get(0);

        try {
            token.curveCompletedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                    .subscribe(event -> {
                        logger.info("CurveCompleted for artistId {}: ethLiquidity={}, tokenLiquidity={}",
                            artistId, event.ethLiquidity, event.tokenLiquidity);
                        evictAllFinancialCaches();
                        Financials updatedFinancials = computeFinancials(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                        messagingTemplate.convertAndSend("/topic/curveCompleted/" + artistId,
                            "Curve completed for " + artistId + ": Uniswap pool created with " + event.ethLiquidity + " ETH and " + event.tokenLiquidity + " tokens");
                    }, error -> logger.error("Error in CurveCompleted subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to CurveCompleted events for contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private BigInteger getEthUsdPrice(ArtistSharesToken token, String contractAddress) {
        try {
            BigInteger price = token.getEthUsdPrice().send();
            logger.debug("Fetched ETH/USD price for contract {}: {}", contractAddress, price);
            if (price == null || price.equals(BigInteger.ZERO)) {
                logger.warn("Invalid ETH/USD price for contract {}, using fallback $3500", contractAddress);
                return FALLBACK_ETH_USD_PRICE;
            }
            return price;
        } catch (Exception e) {
            logger.warn("Failed to fetch ETH/USD price for contract {}, using fallback $3500: {}", contractAddress, e.getMessage());
            return FALLBACK_ETH_USD_PRICE;
        }
    }

    private void updateCandlesForTrade(Trade trade) {
        if (trade == null || trade.getTxHash() == null || trade.getTxHash().trim().isEmpty()) {
            logger.error("BLOCKED: updateCandlesForTrade called with null or empty trade/txHash");
            return;
        }

        String artistId = trade.getArtistId();
        BigDecimal priceUsd = trade.getPriceInUsd();  // ← KEEP FULL PRECISION
        BigDecimal volumeTokens = new BigDecimal(trade.getAmount());
        String eventType = trade.getEventType().name();
        LocalDateTime tradeTimestamp = trade.getTimestamp();

        logger.info("Processing candle update: artistId={}, priceUsd={}, volTokens={}",
            artistId, priceUsd.stripTrailingZeros().toPlainString(), volumeTokens);

        for (Timeframe tf : Timeframe.values()) {
            try {
                long intervalSeconds = tf.getIntervalSeconds();
                long epochSeconds = tradeTimestamp.atZone(ZoneId.of("UTC")).toEpochSecond();
                long periodStartEpoch = (epochSeconds / intervalSeconds) * intervalSeconds;
                LocalDateTime periodTimestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(periodStartEpoch), ZoneId.of("UTC")).withNano(0);

                CandleData candle = candleDataRepository.findByArtistIdAndTimeframeAndTimestamp(
                    artistId, tf, periodTimestamp);

                if (candle == null) {
                    candle = new CandleData(
                        artistId, tf, periodTimestamp,
                        priceUsd, priceUsd, priceUsd, priceUsd, volumeTokens
                    );
                    logger.info("Creating new candle for {}: price={}",
                        tf, priceUsd.stripTrailingZeros().toPlainString());
                } else {
                    candle.setHigh(candle.getHigh().max(priceUsd));
                    candle.setLow(candle.getLow().min(priceUsd));
                    candle.setClose(priceUsd);
                    candle.setVolume(candle.getVolume().add(volumeTokens));
                    
                    logger.info("Updating candle for {}: H={} → {} | L={} → {} | C={}",
                        tf,
                        candle.getHigh().stripTrailingZeros().toPlainString(),
                        candle.getHigh().max(priceUsd).stripTrailingZeros().toPlainString(),
                        candle.getLow().stripTrailingZeros().toPlainString(),
                        candle.getLow().min(priceUsd).stripTrailingZeros().toPlainString(),
                        priceUsd.stripTrailingZeros().toPlainString());
                }

                candle.setLastEventType(Trade.EventType.valueOf(eventType));
                candleDataService.saveCandleData(candle);

            } catch (Exception e) {
                logger.error("Failed to update candle for artistId={}, tf={}, error={}",
                    artistId, tf, e.getMessage(), e);
            }
        }
    }

   @Cacheable(value = "financials", key = "#artistId")
    public Financials computeFinancials(String artistId) {
        logger.info("=== COMPUTING FINANCIALS FOR {} ===", artistId);

        try {
            String contractAddress = contractService.getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId {}", artistId);
                return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0, null, 0.0);
            }

            ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
            BigInteger ethUsdPrice = getEthUsdPrice(token, contractAddress);

            // 24h Volume
            LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("UTC")).minusHours(24);
            BigDecimal volume24hUsd = tradeRepository.sumVolume24hUsd(artistId, cutoff);
            if (volume24hUsd == null) volume24hUsd = BigDecimal.ZERO;

            // Core data
            BigInteger totalSupply = token.totalSupply().send();
            BigInteger tokensSoldRaw = token.tokensSold().send();
            BigInteger tokensInCurveRaw = token.tokensInCurve().send();
            BigInteger ethInCurveRaw = token.ethInCurve().send();

            BigDecimal totalSupplyTokens = new BigDecimal(totalSupply).divide(new BigDecimal("1e18"), 18, RoundingMode.HALF_UP);
            BigDecimal soldTokens = new BigDecimal(tokensSoldRaw).divide(new BigDecimal("1e18"), 18, RoundingMode.HALF_UP);

            // Virtual price for display
            BigInteger priceMicroCents = token.getCurrentPriceMicroUSD().send();
            BigDecimal displayPriceUsd = new BigDecimal(priceMicroCents)
                .divide(new BigDecimal("100000000"), 10, RoundingMode.HALF_UP);

            // Real marginal price
            BigInteger oneToken = BigInteger.TEN.pow(18);
            BigInteger ethOutForOneRaw = token.getEthForTokens(oneToken).send();
            BigDecimal marginalPriceUsd = new BigDecimal(ethOutForOneRaw)
                .divide(new BigDecimal("1e18"), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(ethUsdPrice).divide(new BigDecimal("1e8"), 2, RoundingMode.HALF_UP));

            // Market cap
            BigDecimal realValueUsd = soldTokens.multiply(marginalPriceUsd);
            BigDecimal unsoldTokens = totalSupplyTokens.subtract(soldTokens);
            BigDecimal virtualValueUsd = unsoldTokens.multiply(displayPriceUsd);
            BigDecimal marketCapUsd = realValueUsd.add(virtualValueUsd);

            // LOGS HERE — AFTER ALL VALUES ARE CALCULATED
            logger.info("displayPriceUsd raw: {}", displayPriceUsd);
            logger.info("volume24hUsd raw: {}", volume24hUsd);
            logger.info("marketCapUsd raw: {}", marketCapUsd);
            logger.info("formatUsd(displayPriceUsd): {}", formatUsd(displayPriceUsd));
            logger.info("formatUsd(volume24hUsd): {}", formatUsd(volume24hUsd));
            logger.info("formatUsd(marketCapUsd): {}", formatUsd(marketCapUsd));

            // Rest of your logic (progress, liquidity, etc.)
            BigDecimal ethInCurve = new BigDecimal(ethInCurveRaw).divide(new BigDecimal("1e18"), 6, RoundingMode.HALF_UP);
            BigDecimal targetEth = new BigDecimal("19.7");
            double curveProgress = ethInCurve.divide(targetEth, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
            if (curveProgress > 100.0) curveProgress = 100.0;

            BigInteger dailySellLimitUsd = token.dailySellLimitUsd().send();
            BigDecimal dailyLiquidityUsd = new BigDecimal(dailySellLimitUsd)
                .divide(ETH_USD_SCALE, 2, RoundingMode.HALF_UP);

            BigDecimal ethInCurveUsd = ethInCurve.multiply(new BigDecimal(ethUsdPrice)
                .divide(new BigDecimal("1e8"), 2, RoundingMode.HALF_UP));

            long currentTimestamp = System.currentTimeMillis() / 1000;
            long secondsInDay = 86400;
            long secondsSinceLastReset = currentTimestamp % secondsInDay;
            long secondsToNextReset = secondsInDay - secondsSinceLastReset;
            LocalDateTime nextReset = LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(secondsToNextReset);

            long availableSupply = tokensInCurveRaw.divide(BigInteger.TEN.pow(18)).longValue();

            return new Financials(
                formatUsd(displayPriceUsd),
                formatUsd(volume24hUsd),
                formatUsd(marketCapUsd),
                dailyLiquidityUsd.doubleValue(),
                curveProgress,
                availableSupply,
                nextReset,
                ethInCurveUsd.doubleValue()
            );

        } catch (Exception e) {
            logger.error("Failed to compute financials for artistId {}: {}", artistId, e.getMessage(), e);
            return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0, null, 0.0);
        }
    }

    @Cacheable(value = "userFinancials", key = "#userAddress")
    public BigDecimal computeUserTradeVolume(String userAddress) {
        logger.info("Computing trade volume for userAddress: {}", userAddress);
        try {
            if (!isValidEthereumAddress(userAddress)) {
                logger.warn("Invalid user address: {}", userAddress);
                return BigDecimal.ZERO;
            }

            LocalDateTime twentyFourHoursAgo = LocalDateTime.now(ZoneId.of("UTC")).minusHours(24);
            logger.debug("Querying trades for userAddress: {}, since: {}", userAddress, twentyFourHoursAgo);
            BigDecimal totalUsd = tradeRepository.sumAmountInUsdLast24h(userAddress, twentyFourHoursAgo);
            logger.debug("Query result for userAddress {}: totalUsd={}", userAddress, totalUsd);

            if (totalUsd == null || totalUsd.compareTo(BigDecimal.ZERO) <= 0) {
                logger.info("No trades found for userAddress {} in last 24 hours", userAddress);
                return BigDecimal.ZERO;
            }

            return totalUsd.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            logger.error("Failed to compute trade volume for userAddress {}: {}", userAddress, e.getMessage(), e);
            throw new RuntimeException("Error computing user trade volume for address: " + userAddress, e);
        }
    }

    @CacheEvict(allEntries = true, value = {"financials", "prices", "volumes", "contractAddresses"})
    public void evictAllFinancialCaches() {
        logger.info("Evicting ALL financials-related caches INCLUDING contractAddresses");
    }

    @CacheEvict(value = "userFinancials", key = "#userAddress")
    public void evictUserTradeVolumeCache(String userAddress) {
        logger.debug("Evicted userFinancials cache for {}", userAddress);
    }

    private BigInteger getRecentBlockNumber() {
        try {
            EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
            BigInteger latestBlock = blockNumber.getBlockNumber();
            BigInteger startBlock = latestBlock.subtract(BigInteger.valueOf(BLOCKS_PER_DAY * BACKFILL_DAYS));
            return startBlock.compareTo(BigInteger.ZERO) > 0 ? startBlock : BigInteger.ZERO;
        } catch (Exception e) {
            logger.error("Failed to fetch current block number, using 0: {}", e.getMessage());
            return BigInteger.ZERO;
        }
    }

    private Trade createTradeFromBuyEvent(
            ArtistSharesToken.SharesBoughtEventResponse event,
            String artistId,
            String contractAddress,
            BigInteger ethUsdPrice) {

        Trade trade = new Trade();
        trade.setArtistId(artistId);
        trade.setContractAddress(contractAddress);
        trade.setEventType(Trade.EventType.BUY);
        trade.setTxHash(event.log.getTransactionHash());

        // === EXACT ETH SPENT FROM EVENT (thanks to your Solidity fix!) ===
        BigInteger ethWeiSpent = event.ethSpent != null ? event.ethSpent : BigInteger.ZERO;
        BigDecimal ethSpent = new BigDecimal(ethWeiSpent)
                .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

        BigDecimal ethPriceUsd = new BigDecimal(ethUsdPrice != null ? ethUsdPrice : FALLBACK_ETH_USD_PRICE)
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);

        BigDecimal amountInUsd = ethSpent.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountTokens = new BigDecimal(event.amount)
                .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

        BigInteger priceMicroCents = event.priceMicroCents;
        
        BigDecimal priceUsd = new BigDecimal(priceMicroCents)
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);

        trade.setAmount(amountTokens.toPlainString());
        trade.setPriceInUsd(priceUsd);
        trade.setAmountInUsd(amountInUsd);  // ← Now PERFECT $1507+
        trade.setPrice(priceMicroCents.toString());
        trade.setEthValue(ethSpent.toPlainString());

        trade.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(event.timestamp.longValue()), ZoneId.of("UTC")));
        trade.setBuyerOrSeller(event.buyer);

        logger.info("BUY PERFECT: {} → {} tokens @ final ${} | SPENT {} ETH = ${} | tx={}",
                artistId,
                amountTokens.stripTrailingZeros().toPlainString(),
                priceUsd.stripTrailingZeros().toPlainString(),
                ethSpent.stripTrailingZeros().toPlainString(),
                amountInUsd.stripTrailingZeros().toPlainString(),
                event.log.getTransactionHash().substring(0, 10));

        return trade;
    }

    private Trade createTradeFromSellEvent(
            ArtistSharesToken.SharesSoldEventResponse event,
            String artistId,
            String contractAddress,
            BigInteger ethUsdPrice) {

        Trade trade = new Trade();
        trade.setArtistId(artistId);
        trade.setContractAddress(contractAddress);
        trade.setEventType(Trade.EventType.SELL);
        trade.setTxHash(event.log.getTransactionHash());

        // === EXACT ETH RECEIVED FROM EVENT (after your Solidity fix) ===
        BigInteger ethWeiReceived = event.ethReceived != null ? event.ethReceived : BigInteger.ZERO;
        BigDecimal ethReceived = new BigDecimal(ethWeiReceived)
                .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

        BigDecimal ethPriceUsd = new BigDecimal(ethUsdPrice != null ? ethUsdPrice : FALLBACK_ETH_USD_PRICE)
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);

        BigDecimal amountInUsd = ethReceived.multiply(ethPriceUsd).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountTokens = new BigDecimal(event.amount)
                .divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

        BigInteger priceMicroCents = event.priceMicroCents;

        BigDecimal priceUsd = new BigDecimal(priceMicroCents)
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);

        trade.setAmount(amountTokens.toPlainString());
        trade.setPriceInUsd(priceUsd);
        trade.setAmountInUsd(amountInUsd);
        trade.setPrice(priceMicroCents.toString());
        trade.setEthValue(ethReceived.toPlainString());

        trade.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(event.timestamp.longValue()), ZoneId.of("UTC")));
        trade.setBuyerOrSeller(event.seller);

        logger.info("SELL PERFECT: {} → {} tokens @ ${} | RECEIVED {} ETH = ${}",
                artistId,
                amountTokens.stripTrailingZeros().toPlainString(),
                priceUsd.stripTrailingZeros().toPlainString(),
                ethReceived.stripTrailingZeros().toPlainString(),
                amountInUsd.stripTrailingZeros().toPlainString());

        return trade;
    }

    private void backfillHistoricalTrades() {
        List<String> contractAddresses = artistRepository.findAllContractAddresses();
        logger.info("Backfilling historical trades for contracts: {}", contractAddresses);
        for (String contractAddress : contractAddresses) {
            if (contractAddress == null || contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
                logger.debug("Skipping backfill for invalid contractAddress: {}", contractAddress);
                continue;
            }
            if (!isValidEthereumAddress(contractAddress)) {
                logger.warn("Invalid Ethereum address format: {}", contractAddress);
                continue;
            }

            List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
            if (artistIds.isEmpty()) {
                logger.warn("Skipping backfill: No artistId for contract {}", contractAddress);
                continue;
            }
            String artistId = artistIds.get(0);

            long tradeCount = tradeRepository.countByArtistId(artistId);
            if (tradeCount > 0 && !devMode) {
                logger.info("Skipping backfill for artistId {}: DB has {} trades", artistId, tradeCount);
                continue;
            }

            try {
                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                BigInteger ethUsdPrice = getEthUsdPrice(token, contractAddress);

                BigInteger startBlock = getRecentBlockNumber();
                EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(startBlock),
                    DefaultBlockParameterName.LATEST,
                    contractAddress
                );
                filter.addSingleTopic(EventEncoder.encode(ArtistSharesToken.SHARESBOUGHT_EVENT));
                filter.addSingleTopic(EventEncoder.encode(ArtistSharesToken.SHARESSOLD_EVENT));

                logger.info("Backfilling BUY events for contract {} from block {}", contractAddress, startBlock);
                try {
                    token.sharesBoughtEventFlowable(filter)
                        .timeout(30, TimeUnit.SECONDS)
                        .blockingForEach(event -> {
                            String txHash = event.log.getTransactionHash();
                            if (processedTxHashes.contains(txHash)) {
                                logger.debug("Skipping already processed backfill BUY for txHash: {}", txHash);
                                return;
                            }
                            if (tradeRepository.findByTxHash(txHash).isPresent()) {
                                logger.debug("Skipping duplicate BUY trade for txHash: {}", txHash);
                                processedTxHashes.add(txHash);
                                return;
                            }
                            processedTxHashes.add(txHash);
                            Trade trade = createTradeFromBuyEvent(event, artistId, contractAddress, ethUsdPrice);
                            tradeRepository.save(trade);
                            updateCandlesForTrade(trade);
                            Financials updatedFinancials = computeFinancials(artistId);
                            messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                            logger.debug("Backfilled BUY trade for artistId {}: txHash={}", artistId, trade.getTxHash());
                        });
                } catch (Exception e) {
                    logger.error("Error backfilling BUY events for {}: {}", artistId, e.getMessage(), e);
                }

                logger.info("Backfilling SELL events for contract {} from block {}", contractAddress, startBlock);
                try {
                    token.sharesSoldEventFlowable(filter)
                        .timeout(30, TimeUnit.SECONDS)
                        .blockingForEach(event -> {
                            String txHash = event.log.getTransactionHash();
                            if (processedTxHashes.contains(txHash)) {
                                logger.debug("Skipping already processed backfill SELL for txHash: {}", txHash);
                                return;
                            }
                            if (tradeRepository.findByTxHash(txHash).isPresent()) {
                                logger.debug("Skipping duplicate SELL trade for txHash: {}", txHash);
                                processedTxHashes.add(txHash);
                                return;
                            }
                            processedTxHashes.add(txHash);
                            Trade trade = createTradeFromSellEvent(event, artistId, contractAddress, ethUsdPrice);
                            tradeRepository.save(trade);
                            updateCandlesForTrade(trade);
                            Financials updatedFinancials = computeFinancials(artistId);
                            messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                            logger.debug("Backfilled SELL trade for artistId {}: txHash={}", artistId, trade.getTxHash());
                        });
                } catch (Exception e) {
                    logger.error("Error backfilling SELL events for {}: {}", artistId, e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("Failed to process backfill for contract {}: {}", contractAddress, e.getMessage(), e);
            }
        }
        logger.info("Completed backfilling historical trades for all contracts");
    }

    private boolean isValidEthereumAddress(String address) {
        return address != null && ETHEREUM_ADDRESS_PATTERN.matcher(address).matches();
    }

    private BigDecimal convertWeiToUsd(BigInteger weiValue, BigInteger ethUsdPrice) {
        if (weiValue == null || weiValue.equals(BigInteger.ZERO)) {
            return BigDecimal.ZERO;
        }
        BigInteger effectiveEthUsdPrice = (ethUsdPrice == null || ethUsdPrice.equals(BigInteger.ZERO))
            ? FALLBACK_ETH_USD_PRICE : ethUsdPrice;
        BigDecimal weiToEth = new BigDecimal(weiValue).divide(WEI_TO_ETH, 18, RoundingMode.HALF_UP);
        BigDecimal usdPrice = new BigDecimal(effectiveEthUsdPrice).divide(ETH_USD_SCALE, 8, RoundingMode.HALF_UP);
        logger.debug("convertWeiToUsd: weiValue={}, weiToEth={}, usdPrice={}", weiValue, weiToEth, usdPrice);
        return weiToEth.multiply(usdPrice).setScale(12, RoundingMode.HALF_UP);
    }

    private String formatUsd(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return "$0.00";
        BigDecimal absValue = value.abs();

        if (absValue.compareTo(new BigDecimal("0.000001")) < 0) {
            // For micro prices: show full precision, no scientific notation
            String plain = absValue.setScale(12, RoundingMode.HALF_UP).toPlainString();
            return "$" + plain;
        }

        if (absValue.compareTo(new BigDecimal("0.01")) < 0) {
            String plain = absValue.setScale(8, RoundingMode.HALF_UP).toPlainString();
            return "$" + plain;
        }

        // Rest of your logic (K, M, B)
        if (absValue.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP) + "B";
        } else if (absValue.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP) + "M";
        } else if (absValue.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP) + "K";
        }

        return "$" + absValue.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal calculatePriceFromCurve(ArtistSharesToken token) {
        try {
            BigInteger tokensSold = token.tokensSold().send();
            BigInteger ethInCurve = token.ethInCurve().send();
            BigInteger ethUsdPrice = token.getEthUsdPrice().send();

            if (tokensSold == null || ethInCurve == null || ethUsdPrice == null) return null;
            if (tokensSold.compareTo(BigInteger.ZERO) == 0) {
                return new BigDecimal("50").divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
            }

            // priceWeiPerToken = (1_000_000_000_000 * ethInCurve) / tokensSold
            BigDecimal priceWeiPerToken = new BigDecimal("1000000000000")
                .multiply(new BigDecimal(ethInCurve))
                .divide(new BigDecimal(tokensSold), 18, RoundingMode.HALF_UP);

            // priceMicroUSD = (priceWeiPerToken * ethUsdPrice) / 1e26
            BigDecimal priceMicro = priceWeiPerToken
                .multiply(new BigDecimal(ethUsdPrice))
                .divide(new BigDecimal("100000000000000000000000000"), 8, RoundingMode.HALF_UP);

            return priceMicro.divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);

        } catch (Exception e) {
            logger.warn("Curve price calculation failed: {}", e.getMessage());
            return null;
        }
    }

    public Financials computeFinancialsUncached(String artistId) {
        try {
            // Force evict for this specific key to ensure fresh compute
            Cache cache = cacheManager.getCache("financials");
            if (cache != null) {
                cache.evict(artistId);
                logger.info("Forced eviction for financials cache key: {}", artistId);
            }
            // Now recompute fresh
            return computeFinancials(artistId);
        } catch (Exception e) {
            logger.error("Failed to compute uncached financials for {}: {}", artistId, e.getMessage(), e);
            return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0L, null, 0.0);
        }
    }
}