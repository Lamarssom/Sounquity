package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.contract.ArtistSharesToken.SharesBoughtEventResponse;
import com.musicinvestment.musicapp.contract.ArtistSharesToken.SharesSoldEventResponse;
import com.musicinvestment.musicapp.model.Trade;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import com.musicinvestment.musicapp.repository.TradeRepository;
import com.musicinvestment.musicapp.model.Financials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BlockchainSyncService {

    private static final Logger logger = LoggerFactory.getLogger(BlockchainSyncService.class);
    private static final Pattern ETHEREUM_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");

    private final Web3j web3j;
    private final ArtistSharesFactory artistSharesFactory;
    private final ArtistRepository artistRepository;
    private final TradeRepository tradeRepository;
    private final String privateKey;

    public BlockchainSyncService(
            Web3j web3j,
            @Value("${contract.artist-shares-factory-address}") String factoryAddress,
            @Value("${web3j.private-key}") String privateKey,
            ArtistRepository artistRepository,
            TradeRepository tradeRepository) {
        this.web3j = web3j;
        this.artistRepository = artistRepository;
        this.tradeRepository = tradeRepository;
        this.privateKey = privateKey;
        Credentials credentials = Credentials.create(privateKey);
        TransactionManager txManager = new FastRawTransactionManager(web3j, credentials);
        this.artistSharesFactory = ArtistSharesFactory.load(
                factoryAddress, web3j, txManager, new DefaultGasProvider());
    }

    @PostConstruct
    public void init() {
        syncArtistContracts();
        subscribeToTradeEvents();
    }

    private void syncArtistContracts() {
        try {
            List<String> deployedTokens = artistSharesFactory.getDeployedTokens().send();
            for (String contractAddress : deployedTokens) {
                if (isValidEthereumAddress(contractAddress)) {
                    String artistId = artistRepository.findArtistIdByContractAddress(contractAddress)
                            .orElse(null);
                    if (artistId == null) {
                        // Query factory to get artistId
                        String fetchedArtistId = artistSharesFactory.getTokenByArtistId(contractAddress).send();
                        if (fetchedArtistId != null && !fetchedArtistId.isEmpty()) {
                            artistRepository.updateContractAddress(fetchedArtistId, contractAddress);
                            logger.info("Synced contract {} for artistId {}", contractAddress, fetchedArtistId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing artist contracts: {}", e.getMessage());
        }
    }

    private void subscribeToTradeEvents() {
        List<String> contractAddresses = artistRepository.findAllContractAddresses();
        for (String contractAddress : contractAddresses) {
            if (isValidEthereumAddress(contractAddress)) {
                ArtistSharesToken token = loadTokenContract(contractAddress);
                subscribeToSharesBoughtEvents(token, contractAddress);
                subscribeToSharesSoldEvents(token, contractAddress);
            }
        }
    }

    private void subscribeToSharesBoughtEvents(ArtistSharesToken token, String contractAddress) {
        token.sharesBoughtEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Optional<String> artistIdOpt = artistRepository.findArtistIdByContractAddress(contractAddress);
                    if (artistIdOpt.isPresent()) {
                        Trade trade = new Trade();
                        trade.setArtistId(artistIdOpt.get());
                        trade.setContractAddress(contractAddress);
                        trade.setEventType(Trade.EventType.BUY);
                        trade.setAmount(event.amount);
                        trade.setPrice(event.price);
                        // Account for 2% fee: totalAfterFee = (price * amount * 98 ) 
                        trade.setEthValue(event.price.multiply(event.amount).multiply(BigInteger.valueOf(98)).divide(BigInteger.valueOf(100)));
                        trade.setTimestamp(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochSecond(event.timestamp.longValue()),
                                ZoneId.systemDefault()));
                        trade.setBuyerOrSeller(event.buyer);
                        tradeRepository.save(trade);
                        logger.info("Saved SharesBought event for artistId {}: amount={}, price={}, ethValue={}",
                                artistIdOpt.get(), event.amount, event.price, trade.getEthValue());
                    } else {
                        logger.warn("No artistId found for contractAddress {}", contractAddress);
                    }
                }, error -> logger.error("Error in SharesBought subscription for {}: {}", contractAddress, error.getMessage()));
    }

    private void subscribeToSharesSoldEvents(ArtistSharesToken token, String contractAddress) {
        token.sharesSoldEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Optional<String> artistIdOpt = artistRepository.findArtistIdByContractAddress(contractAddress);
                    if (artistIdOpt.isPresent()) {
                        Trade trade = new Trade();
                        trade.setArtistId(artistIdOpt.get());
                        trade.setContractAddress(contractAddress);
                        trade.setEventType(Trade.EventType.SELL);
                        trade.setAmount(event.amount);
                        trade.setPrice(event.price);
                        // Account for 2% fee: payout = (price * amount * 98)
                        trade.setEthValue(event.price.multiply(event.amount).multiply(BigInteger.valueOf(98)).divide(BigInteger.valueOf(100)));
                        trade.setTimestamp(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochSecond(event.timestamp.longValue()),
                                ZoneId.systemDefault()));
                        trade.setBuyerOrSeller(event.seller);
                        tradeRepository.save(trade);
                        logger.info("Saved SharesSold event for artistId {}: amount={}, price={}, ethValue={}",
                                artistIdOpt.get(), event.amount, event.price, trade.getEthValue());
                    } else {
                        logger.warn("No artistId found for contractAddress {}", contractAddress);
                    }
                }, error -> logger.error("Error in SharesSold subscription for {}: {}", contractAddress, error.getMessage()));
    }

    public String getCurrentPrice(String artistId) {
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("Skipping getCurrentPrice: No valid contract address for artistId: {}", artistId);
                return "0";
            }
            ArtistSharesToken token = loadTokenContract(contractAddress);
            return token.getCurrentPrice().send().toString();
        } catch (Exception e) {
            logger.error("Error fetching price for artistId {}: {}", artistId, e.getMessage());
            return "0";
        }
    }

    public String getTotalVolumeTraded(String artistId) {
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("Skipping getTotalVolumeTraded: No valid contract address for artistId: {}", artistId);
                return "0";
            }
            ArtistSharesToken token = loadTokenContract(contractAddress);
            return token.getTotalVolumeTraded().send().toString();
        } catch (Exception e) {
            logger.error("Error fetching volume for artistId {}: {}", artistId, e.getMessage());
            return "0";
        }
    }

    public void buyShares(String artistId, BigInteger amount, BigInteger weiValue) throws Exception {
        String contractAddress = getContractAddress(artistId);
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            throw new IllegalArgumentException("Cannot buy shares: No valid contract address for artistId: " + artistId);
        }
        ArtistSharesToken token = loadTokenContract(contractAddress);
        token.buyShares(amount, weiValue).send();
    }

    public void sellShares(String artistId, BigInteger amount) throws Exception {
        String contractAddress = getContractAddress(artistId);
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            throw new IllegalArgumentException("Cannot sell shares: No valid contract address for artistId: " + artistId);
        }
        ArtistSharesToken token = loadTokenContract(contractAddress);
        token.sellShares(amount).send();
    }

    private String getContractAddress(String artistId) throws Exception {
        Optional<String> contractAddress = artistRepository.findContractAddressByArtistId(artistId);
        if (contractAddress.isPresent() && isValidEthereumAddress(contractAddress.get())) {
            return contractAddress.get();
        }
        // Fallback: Query factory contract
        String address = artistSharesFactory.getTokenByArtistId(artistId).send();
        if (address != null && isValidEthereumAddress(address)) {
            artistRepository.updateContractAddress(artistId, address);
            return address;
        }
        return null;
    }

    protected ArtistSharesToken loadTokenContract(String contractAddress) {
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            throw new IllegalArgumentException("Invalid contract address: " + contractAddress);
        }
        Credentials credentials = Credentials.create(privateKey);
        TransactionManager txManager = new FastRawTransactionManager(web3j, credentials);
        return ArtistSharesToken.load(contractAddress, web3j, txManager, new DefaultGasProvider());
    }

    private boolean isValidEthereumAddress(String address) {
        return address != null && ETHEREUM_ADDRESS_PATTERN.matcher(address).matches();
    }

    // New method for financials computation
    @Cacheable(value = "financials", key = "#artistId")
    public Financials computeFinancials(String artistId) {
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract for artistId: {}", artistId);
                return new Financials("N/A", "N/A", "N/A");
            }
            ArtistSharesToken token = loadTokenContract(contractAddress);

            // Get current price in wei
            BigInteger priceWei = token.getCurrentPrice().send();

            // Get ETH/USD price from mock Chainlink feed (returns price with 8 decimals)
            BigInteger ethUsdPrice = token.getEthUsdPrice().send();  // e.g., 300000000000 for $3000 with 8 decimals

            // Convert price to USD (priceWei / 10^18 * ethUsdPrice / 10^8)
            BigDecimal priceUsd = convertWeiToUsd(priceWei, ethUsdPrice);

            // Get total supply
            BigInteger totalSupply = token.totalSupply().send();

            // Market Cap = totalSupply * priceUsd
            BigDecimal marketCapUsd = new BigDecimal(totalSupply).multiply(priceUsd);

            // 24h Volume from trades table (sum ethValue converted to USD)
            LocalDateTime since = LocalDateTime.now(ZoneId.systemDefault()).minusHours(24);
            BigInteger volumeWei = tradeRepository.sumEthValueLast24h(artistId, since);
            BigDecimal volumeUsd = convertWeiToUsd(volumeWei, ethUsdPrice);

            return new Financials(
                    formatUsd(priceUsd),
                    formatUsd(volumeUsd),
                    formatUsd(marketCapUsd)
            );
        } catch (Exception e) {
            logger.error("Error computing financials for artistId {}: {}", artistId, e.getMessage());
            return new Financials("N/A", "N/A", "N/A");
        }
    }

    // Helper: Convert wei value to USD using ETH/USD price
    private BigDecimal convertWeiToUsd(BigInteger weiValue, BigInteger ethUsdPrice) {
        if (weiValue == null || weiValue.equals(BigInteger.ZERO)) return BigDecimal.ZERO;
        BigDecimal weiToEth = new BigDecimal(weiValue).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);
        BigDecimal usdPrice = new BigDecimal(ethUsdPrice).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
        return weiToEth.multiply(usdPrice);
    }

    // Helper: Format as USD with suffixes (e.g., $1.23K)
    private String formatUsd(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return "$0.00";
        double absValue = value.abs().doubleValue();
        if (absValue >= 1e9) {
            return "$" + value.divide(BigDecimal.valueOf(1e9), 2, RoundingMode.HALF_UP) + "B";
        } else if (absValue >= 1e6) {
            return "$" + value.divide(BigDecimal.valueOf(1e6), 2, RoundingMode.HALF_UP) + "M";
        } else if (absValue >= 1e3) {
            return "$" + value.divide(BigDecimal.valueOf(1e3), 2, RoundingMode.HALF_UP) + "K";
        }
        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }
}