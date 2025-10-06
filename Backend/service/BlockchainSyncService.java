package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.model.Trade;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import com.musicinvestment.musicapp.repository.TradeRepository;
import com.musicinvestment.musicapp.model.Financials;
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
import org.springframework.beans.factory.annotation.Autowired;
import com.musicinvestment.musicapp.model.CandleData;
import com.musicinvestment.musicapp.model.Timeframe;
import com.musicinvestment.musicapp.repository.CandleDataRepository;
import com.musicinvestment.musicapp.service.CandleDataService;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class BlockchainSyncService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainSyncService.class);
    private static final Pattern ETHEREUM_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final BigDecimal WEI_TO_ETH = new BigDecimal("1000000000000000000"); // 10^18
    private static final BigDecimal ETH_USD_SCALE = new BigDecimal("100000000"); // 10^8
    private static final BigDecimal FALLBACK_PRICE = new BigDecimal("3.50"); // Use $3.50 for testing
    private static final BigInteger FALLBACK_ETH_USD_PRICE = BigInteger.valueOf(350000000000L); // $3500 * 10^8
    private static final long BLOCKS_PER_DAY = 5760; // Approx. 15s block time, 24h = 86400s / 15 = 5760 blocks
    private static final long BACKFILL_DAYS = 30; // Backfill last 30 days only

    private final Web3j web3j;
    private final ArtistSharesFactory artistSharesFactory;
    private final ArtistRepository artistRepository;
    private final TradeRepository tradeRepository;
    private final String privateKey;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;
    private final ContractService contractService;
    private final Set<String> subscribedContracts = new HashSet<>();
    private final ExecutorService backfillExecutor = Executors.newSingleThreadExecutor();

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Autowired
    private CandleDataService candleDataService;

    @Autowired
    private CandleDataRepository candleDataRepository;

    @Autowired
    public BlockchainSyncService(
            Web3j web3j,
            ArtistSharesFactory artistSharesFactory,
            @Value("${web3j.private-key}") String privateKey,
            ArtistRepository artistRepository,
            TradeRepository tradeRepository,
            SimpMessagingTemplate messagingTemplate,
            CacheManager cacheManager,
            ContractService contractService) {
        this.web3j = web3j;
        this.artistSharesFactory = artistSharesFactory;
        this.artistRepository = artistRepository;
        this.tradeRepository = tradeRepository;
        this.privateKey = privateKey;
        this.messagingTemplate = messagingTemplate;
        this.cacheManager = cacheManager;
        this.contractService = contractService;
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
            logger.info("Dev mode: Cleared trades and candles for clean start.");
        }
        syncArtistContracts();
        subscribeToTradeEvents();
        backfillExecutor.submit(this::backfillHistoricalTrades);
        backfillHistoricalCandles();
    }

    private void backfillHistoricalCandles() {
        List<String> artistIds = artistRepository.findAll().stream()
                .map(artist -> artist.getId())
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

            long interval = getIntervalSeconds(tf);
            LocalDateTime currentPeriod = null;
            CandleData currentCandle = null;
            LocalDateTime cutoff = LocalDateTime.now(ZoneId.systemDefault()).minusDays(30);

            for (Trade trade : trades) {
                LocalDateTime ts = trade.getTimestamp();
                if (ts.isBefore(cutoff)) {
                    logger.debug("Skipping trade for artistId {} before cutoff: {}", artistId, ts);
                    continue;
                }

                long epoch = ts.atZone(ZoneId.systemDefault()).toEpochSecond();
                long periodStartEpoch = (epoch / interval) * interval;
                LocalDateTime period = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodStartEpoch), ZoneId.systemDefault());

                BigDecimal price = BigDecimal.valueOf(trade.getPriceInUsd());
                BigDecimal volAdd = new BigDecimal(trade.getAmount().toString());

                if (currentCandle == null || !period.equals(currentPeriod)) {
                    if (currentCandle != null) {
                        candleDataService.saveCandleData(currentCandle);
                    }
                    currentPeriod = period;
                    currentCandle = new CandleData(artistId, tf, period, price, price, price, price, volAdd);
                    currentCandle.setLastEventType(trade.getEventType().name().toUpperCase());
                } else {
                    currentCandle.setHigh(currentCandle.getHigh().max(price));
                    currentCandle.setLow(currentCandle.getLow().min(price));
                    currentCandle.setClose(price);
                    currentCandle.setVolume(currentCandle.getVolume().add(volAdd));
                    currentCandle.setLastEventType(trade.getEventType().name().toUpperCase());
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

            if (subscribedContracts.contains(contractAddress)) {
                logger.info("Already subscribed to contract: {}", contractAddress);
                continue;
            }
            try {
                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                subscribeToSharesBoughtEvents(token, contractAddress);
                subscribeToSharesSoldEvents(token, contractAddress);
                subscribedContracts.add(contractAddress);
            } catch (Exception e) {
                logger.error("Failed to subscribe to trade events for contract {}: {}", contractAddress, e.getMessage(), e);
            }
        }
    }

    public void subscribeToNewContract(String contractAddress) {
        if (isValidEthereumAddress(contractAddress)) {
            if (subscribedContracts.contains(contractAddress)) {
                logger.info("Already subscribed to new contract: {}", contractAddress);
                return;
            }
            try {
                ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
                subscribeToSharesBoughtEvents(token, contractAddress);
                subscribeToSharesSoldEvents(token, contractAddress);
                logger.info("Subscribed to events for new contract: {}", contractAddress);
                subscribedContracts.add(contractAddress);
            } catch (Exception e) {
                logger.error("Failed to subscribe to new contract {}: {}", contractAddress, e.getMessage(), e);
            }
        } else {
            logger.warn("Cannot subscribe to invalid contract address: {}", contractAddress);
        }
    }

    private void subscribeToSharesBoughtEvents(ArtistSharesToken token, String contractAddress) {
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
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
                        if (tradeRepository.findByTxHash(event.log.getTransactionHash()).isPresent()) {
                            logger.info("Skipping duplicate BUY trade for txHash: {}", event.log.getTransactionHash());
                            return;
                        }
                        Trade trade = createTradeFromBuyEvent(event, artistId, contractAddress, getEthUsdPrice(token, contractAddress));
                        tradeRepository.save(trade);
                        logger.info("Saved SharesBought event for artistId {}: amount={}, price={}, ethValue={}, txHash={}, amountInUsd={}, priceInUsd={}",
                                artistId, event.amount, event.price, trade.getEthValue(), trade.getTxHash(), trade.getAmountInUsd(), trade.getPriceInUsd());

                        evictCaches(artistId);
                        messagingTemplate.convertAndSend("/topic/trades/" + artistId, trade);
                        Financials updatedFinancials = computeFinancials(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                        logger.info("Broadcasted trade for artistId {}: type={}, amountInUsd={}, priceInUsd={}, txHash={}",
                                artistId, trade.getEventType(), trade.getAmountInUsd(), trade.getPriceInUsd(), trade.getTxHash());
                        updateCandlesForTrade(trade);
                    }, error -> logger.error("Error in SharesBought subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to SharesBought events for contract {}: {}", contractAddress, e.getMessage(), e);
        }
    }

    private void subscribeToSharesSoldEvents(ArtistSharesToken token, String contractAddress) {
        List<String> artistIds = artistRepository.findArtistIdsByContractAddress(contractAddress);
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
                        if (tradeRepository.findByTxHash(event.log.getTransactionHash()).isPresent()) {
                            logger.info("Skipping duplicate SELL trade for txHash: {}", event.log.getTransactionHash());
                            return;
                        }
                        Trade trade = createTradeFromSellEvent(event, artistId, contractAddress, getEthUsdPrice(token, contractAddress));
                        tradeRepository.save(trade);
                        logger.info("Saved SharesSold event for artistId {}: amount={}, price={}, ethValue={}, txHash={}, amountInUsd={}, priceInUsd={}",
                                artistId, event.amount, event.price, trade.getEthValue(), trade.getTxHash(), trade.getAmountInUsd(), trade.getPriceInUsd());

                        evictCaches(artistId);
                        messagingTemplate.convertAndSend("/topic/trades/" + artistId, trade);
                        Financials updatedFinancials = computeFinancials(artistId);
                        messagingTemplate.convertAndSend("/topic/financials/" + artistId, updatedFinancials);
                        logger.info("Broadcasted trade for artistId {}: type={}, amountInUsd={}, priceInUsd={}, txHash={}",
                                artistId, trade.getEventType(), trade.getAmountInUsd(), trade.getPriceInUsd(), trade.getTxHash());
                        updateCandlesForTrade(trade);
                    }, error -> logger.error("Error in SharesSold subscription for {}: {}", contractAddress, error.getMessage(), error));
        } catch (Exception e) {
            logger.error("Failed to subscribe to SharesSold events for contract {}: {}", contractAddress, e.getMessage(), e);
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
            logger.warn("Failed to fetch ETH/USD price for contract {}, using fallback $3500: {}", contractAddress, e.getMessage(), e);
            return FALLBACK_ETH_USD_PRICE;
        }
    }

    private void updateCandlesForTrade(Trade trade) {
        String artistId = trade.getArtistId();
        BigDecimal price = BigDecimal.valueOf(trade.getPriceInUsd());
        BigDecimal volAdd = new BigDecimal(trade.getAmount().toString());
        String eventType = trade.getEventType().name().toUpperCase();
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.systemDefault()).minusDays(30);

        for (Timeframe tf : Timeframe.values()) {
            long interval = getIntervalSeconds(tf);
            LocalDateTime ts = trade.getTimestamp();
            if (ts.isBefore(cutoff)) {
                logger.debug("Skipping trade update for artistId {} before cutoff: {}", artistId, ts);
                continue;
            }
            long epoch = ts.atZone(ZoneId.of("UTC")).toEpochSecond();
            long periodStartEpoch = (epoch / interval) * interval;
            LocalDateTime periodTimestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodStartEpoch), ZoneId.of("UTC"));

            logger.debug("Saving candle for artistId {}, timeframe {}, timestamp {}, price {}, volume {}, eventType {}", 
                        artistId, tf.getValue(), periodTimestamp, price, volAdd, eventType);

            CandleData candle = candleDataRepository.findByArtistIdAndTimeframeAndTimestamp(artistId, tf, periodTimestamp);
            if (candle == null) {
                candle = new CandleData(artistId, tf, periodTimestamp, price, price, price, price, volAdd);
            } else {
                candle.setHigh(candle.getHigh().max(price));
                candle.setLow(candle.getLow().min(price));
                candle.setClose(price);
                candle.setVolume(candle.getVolume().add(volAdd));
            }
            candle.setLastEventType(eventType);
            try {
                candleDataService.saveCandleData(candle);
                logger.debug("Saved candle for artistId {}, timeframe {}, timestamp {}", artistId, tf.getValue(), periodTimestamp);
            } catch (Exception e) {
                logger.error("Failed to save candle for artistId {}, timeframe {}, timestamp {}: {}", 
                            artistId, tf.getValue(), periodTimestamp, e.getMessage());
            }
        }
        logger.debug("Updated candles for trade txHash: {}", trade.getTxHash());
    }

    private long getIntervalSeconds(Timeframe tf) {
        Map<Timeframe, Long> intervalMap = new HashMap<>();
        intervalMap.put(Timeframe.ONE_MINUTE, 60L);
        intervalMap.put(Timeframe.FIVE_MINUTES, 300L);
        intervalMap.put(Timeframe.FIFTEEN_MINUTES, 900L);
        intervalMap.put(Timeframe.THIRTY_MINUTES, 1800L);
        intervalMap.put(Timeframe.ONE_HOUR, 3600L);
        intervalMap.put(Timeframe.FOUR_HOURS, 14400L);
        intervalMap.put(Timeframe.ONE_DAY, 86400L);
        intervalMap.put(Timeframe.ONE_WEEK, 604800L);
        return intervalMap.getOrDefault(tf, 300L);
    }

    @Cacheable(value = "financials", key = "#artistId")
    public Financials computeFinancials(String artistId) {
        logger.info("Computing financials for artistId: {}", artistId);
        try {
            String contractAddress = contractService.getContractAddress(artistId);
            logger.debug("Contract address for artistId {}: {}", artistId, contractAddress);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0, null);
            }

            ArtistSharesToken token;
            try {
                token = contractService.loadTokenContract(contractAddress);
                logger.debug("Loaded token contract for address: {}", contractAddress);
            } catch (Exception e) {
                logger.error("Failed to load token contract for address {}: {}", contractAddress, e.getMessage(), e);
                return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0, null);
            }

            BigInteger ethUsdPrice = getEthUsdPrice(token, contractAddress);
            logger.debug("ETH/USD price for contract {}: {}", contractAddress, ethUsdPrice);

            BigInteger dailyLimitWei;
            try {
                dailyLimitWei = token.getDailyLimit().send();
                logger.debug("Daily limit (wei) for contract {}: {}", contractAddress, dailyLimitWei);
            } catch (Exception e) {
                logger.warn("Failed to fetch daily limit for contract {}, assuming 0: {}", contractAddress, e.getMessage(), e);
                dailyLimitWei = BigInteger.ZERO;
            }

            BigInteger globalDailyTradesWei;
            try {
                globalDailyTradesWei = token.getGlobalDailyTrades().send();
                logger.debug("Global daily trades (wei) for contract {}: {}", contractAddress, globalDailyTradesWei);
            } catch (Exception e) {
                logger.warn("Failed to fetch global daily trades for contract {}, assuming 0: {}", contractAddress, e.getMessage(), e);
                globalDailyTradesWei = BigInteger.ZERO;
            }

            BigInteger lastRecordedDay;
            try {
                lastRecordedDay = token.getLastRecordedDay().send();
                logger.debug("Last recorded day for contract {}: {}", contractAddress, lastRecordedDay);
            } catch (Exception e) {
                logger.warn("Failed to fetch last recorded day for contract {}, assuming 0: {}", contractAddress, e.getMessage(), e);
                lastRecordedDay = BigInteger.ZERO;
            }

            BigInteger availableSupplyWei;
            try {
                availableSupplyWei = token.getAvailableSupply().send();
                logger.debug("Available supply (wei) for contract {}: {}", contractAddress, availableSupplyWei);
            } catch (Exception e) {
                logger.warn("Failed to fetch available supply for contract {}, assuming 0: {}", contractAddress, e.getMessage(), e);
                availableSupplyWei = BigInteger.ZERO;
            }

            BigDecimal dailyLiquidityUsd = convertWeiToUsd(dailyLimitWei, ethUsdPrice);
            BigDecimal liquidityPercentage = dailyLimitWei.compareTo(BigInteger.ZERO) > 0
                ? new BigDecimal(dailyLimitWei.subtract(globalDailyTradesWei))
                    .divide(new BigDecimal(dailyLimitWei), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            long availableSupply = availableSupplyWei.divide(BigInteger.TEN.pow(18)).longValue();
            long currentTimestamp = System.currentTimeMillis() / 1000;
            long secondsInDay = 86400;
            long secondsSinceLastReset = lastRecordedDay.equals(BigInteger.ZERO) ? 0 : currentTimestamp - (lastRecordedDay.longValue() * secondsInDay);
            long secondsToNextReset = secondsSinceLastReset <= 0 ? secondsInDay : secondsInDay - secondsSinceLastReset;
            LocalDateTime nextReset = LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(secondsToNextReset);

            BigDecimal priceUsd;
            try {
                BigInteger priceWei = token.getCurrentPrice().send();
                logger.debug("Raw price (wei) for artistId {}: {}", artistId, priceWei);
                priceUsd = convertWeiToUsd(priceWei, ethUsdPrice);
                if (priceUsd == null || priceUsd.compareTo(BigDecimal.ZERO) <= 0) {
                    logger.warn("Zero or negative price for artistId {}, using fallback: {}", artistId, FALLBACK_PRICE);
                    priceUsd = FALLBACK_PRICE;
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch price for artistId {}, using fallback: {}", artistId, e.getMessage(), e);
                priceUsd = FALLBACK_PRICE;
            }

            BigDecimal volumeUsd;
            try {
                BigInteger volumeWei = token.getTotalVolumeTraded().send();
                logger.debug("Raw volume (wei) for artistId {}: {}", artistId, volumeWei);
                volumeUsd = convertWeiToUsd(volumeWei, ethUsdPrice);
            } catch (Exception e) {
                logger.warn("Failed to fetch volume for artistId {}, using database: {}", artistId, e.getMessage(), e);
                volumeUsd = BigDecimal.ZERO;
            }

            LocalDateTime twentyFourHoursAgo = LocalDateTime.now(ZoneId.of("UTC")).minusHours(24);
            BigInteger volumeEthWei = tradeRepository.sumEthValueLast24h(artistId, twentyFourHoursAgo);
            BigDecimal volume24hUsd = volumeEthWei != null ? convertWeiToUsd(volumeEthWei, ethUsdPrice) : BigDecimal.ZERO;
            if (volumeUsd == null || volumeUsd.compareTo(BigDecimal.ZERO) <= 0) {
                volumeUsd = volume24hUsd;
                logger.info("Using database volume for artistId {}: {}", artistId, volume24hUsd);
            }

            BigInteger decimals;
            try {
                decimals = token.decimals().send();
                logger.debug("Decimals for contract {}: {}", contractAddress, decimals);
            } catch (Exception e) {
                logger.warn("Failed to fetch decimals for contract {}, assuming 18: {}", contractAddress, e.getMessage(), e);
                decimals = BigInteger.valueOf(18);
            }

            BigInteger totalSupply;
            try {
                totalSupply = token.totalSupply().send();
                logger.debug("Total supply for contract {}: {}", contractAddress, totalSupply);
            } catch (Exception e) {
                logger.warn("Failed to fetch total supply for contract {}, assuming 0: {}", contractAddress, e.getMessage(), e);
                totalSupply = BigInteger.ZERO;
            }

            BigDecimal tokenScale = new BigDecimal(BigInteger.TEN.pow(decimals.intValue()));
            BigDecimal totalSupplyTokens = totalSupply.compareTo(BigInteger.ZERO) > 0 
                ? new BigDecimal(totalSupply).divide(tokenScale, 18, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
            BigDecimal marketCapUsd = totalSupplyTokens.multiply(priceUsd);

            Financials financials = new Financials(
                formatUsd(priceUsd),
                formatUsd(volume24hUsd),
                formatUsd(marketCapUsd),
                dailyLiquidityUsd.doubleValue(),
                liquidityPercentage.doubleValue(),
                availableSupply,
                nextReset
            );
            logger.debug("Computed financials for artistId {}: price={}, volume24h={}, marketCap={}", 
                        artistId, financials.getCurrentPrice(), financials.getVolume24h(), financials.getMarketCap());
            return financials;
        } catch (Exception e) {
            logger.error("Error computing financials for artistId {}: {}", artistId, e.getMessage(), e);
            return new Financials("$0.00", "$0.00", "$0.00", 0.0, 100.0, 0, null);
        }
    }

    @CacheEvict(value = {"financials", "prices", "volumes"}, key = "#artistId")
    public void evictCaches(String artistId) {
        logger.info("Evicting caches for artistId: {}", artistId);
        for (String cacheName : new String[]{"financials", "prices", "volumes"}) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(artistId);
            }
        }
    }

    public void buyShares(String artistId, BigInteger amount, BigInteger weiValue) throws Exception {
        String contractAddress = contractService.getContractAddress(artistId);
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            throw new IllegalArgumentException("Cannot buy shares: No valid contract address for artistId: " + artistId);
        }
        ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
        token.buyShares(amount, weiValue).send();
        evictCaches(artistId);
    }

    public void sellShares(String artistId, BigInteger amount) throws Exception {
        String contractAddress = contractService.getContractAddress(artistId);
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            throw new IllegalArgumentException("Cannot sell shares: No valid contract address for artistId: " + artistId);
        }
        ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
        token.sellShares(amount).send();
        evictCaches(artistId);
    }

    public BigDecimal getCurrentPriceRaw(String artistId) {
        try {
            String contractAddress = contractService.getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return FALLBACK_PRICE;
            }
            ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
            BigInteger priceWei = token.getCurrentPrice().send();
            BigInteger ethUsdPrice = getEthUsdPrice(token, contractAddress);
            BigDecimal priceUsd = convertWeiToUsd(priceWei, ethUsdPrice);
            return priceUsd.compareTo(BigDecimal.ZERO) > 0 ? priceUsd : FALLBACK_PRICE;
        } catch (Exception e) {
            logger.warn("Failed to fetch price for artistId {}, returning fallback: {}", artistId, FALLBACK_PRICE);
            return FALLBACK_PRICE;
        }
    }

    public BigDecimal getTotalVolumeTradedRaw(String artistId) {
        try {
            String contractAddress = contractService.getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return BigDecimal.ZERO;
            }
            ArtistSharesToken token = contractService.loadTokenContract(contractAddress);
            BigInteger volumeWei = token.getTotalVolumeTraded().send();
            BigInteger ethUsdPrice = getEthUsdPrice(token, contractAddress);
            return convertWeiToUsd(volumeWei, ethUsdPrice);
        } catch (Exception e) {
            logger.warn("Failed to fetch volume for artistId {}, returning zero: {}", artistId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public String getCurrentPrice(String artistId) {
        return formatUsd(getCurrentPriceRaw(artistId));
    }

    public String getTotalVolumeTraded(String artistId) {
        return formatUsd(getTotalVolumeTradedRaw(artistId));
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
        return weiToEth.multiply(usdPrice);
    }

    private String formatUsd(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) return "$0.00";
        BigDecimal absValue = value.abs().setScale(2, RoundingMode.HALF_UP);
        double absDouble = absValue.doubleValue();
        if (absDouble >= 1_000_000_000) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP) + "B";
        } else if (absDouble >= 1_000_000) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP) + "M";
        } else if (absDouble >= 1_000) {
            return "$" + absValue.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP) + "K";
        }
        return "$" + absValue.toString();
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
                            if (tradeRepository.findByTxHash(event.log.getTransactionHash()).isPresent()) {
                                logger.debug("Skipping duplicate BUY trade for txHash: {}", event.log.getTransactionHash());
                                return;
                            }
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
                            if (tradeRepository.findByTxHash(event.log.getTransactionHash()).isPresent()) {
                                logger.debug("Skipping duplicate SELL trade for txHash: {}", event.log.getTransactionHash());
                                return;
                            }
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

    private Trade createTradeFromBuyEvent(ArtistSharesToken.SharesBoughtEventResponse event, String artistId, String contractAddress, BigInteger ethUsdPrice) {
        Trade trade = new Trade();
        trade.setArtistId(artistId);
        trade.setContractAddress(contractAddress);
        trade.setEventType(Trade.EventType.BUY);
        trade.setAmount(event.amount);
        trade.setPrice(event.price);
        trade.setEthValue(event.price.multiply(event.amount).multiply(BigInteger.valueOf(98)).divide(BigInteger.valueOf(100)));
        trade.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochSecond(event.timestamp.longValue()), ZoneId.systemDefault()));
        trade.setBuyerOrSeller(event.buyer);
        trade.setTxHash(event.log.getTransactionHash());
        BigDecimal priceUsd = convertWeiToUsd(event.price, ethUsdPrice);
        BigDecimal amountUsd = convertWeiToUsd(event.amount.multiply(event.price), ethUsdPrice);
        trade.setPriceInUsd(priceUsd.doubleValue());
        trade.setAmountInUsd(amountUsd.doubleValue());
        return trade;
    }

    private Trade createTradeFromSellEvent(ArtistSharesToken.SharesSoldEventResponse event, String artistId, String contractAddress, BigInteger ethUsdPrice) {
        Trade trade = new Trade();
        trade.setArtistId(artistId);
        trade.setContractAddress(contractAddress);
        trade.setEventType(Trade.EventType.SELL);
        trade.setAmount(event.amount);
        trade.setPrice(event.price);
        trade.setEthValue(event.price.multiply(event.amount).multiply(BigInteger.valueOf(98)).divide(BigInteger.valueOf(100)));
        trade.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochSecond(event.timestamp.longValue()), ZoneId.systemDefault()));
        trade.setBuyerOrSeller(event.seller);
        trade.setTxHash(event.log.getTransactionHash());
        BigDecimal priceUsd = convertWeiToUsd(event.price, ethUsdPrice);
        BigDecimal amountUsd = convertWeiToUsd(event.amount.multiply(event.price), ethUsdPrice);
        trade.setPriceInUsd(priceUsd.doubleValue());
        trade.setAmountInUsd(amountUsd.doubleValue());
        return trade;
    }
}
