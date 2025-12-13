package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);
    private static final Pattern ETHEREUM_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal WEI_TO_ETH = new BigDecimal("1000000000000000000"); // 10^18
    private static final BigDecimal ETH_USD_SCALE = new BigDecimal("100000000");
    private static final BigInteger FALLBACK_ETH_USD_PRICE = BigInteger.valueOf(3_500L).multiply(BigInteger.TEN.pow(8)); // $3500 * 10^8

    private final ArtistSharesFactory artistSharesFactory;
    private final ArtistRepository artistRepository;
    private final Web3j web3j;
    private final String privateKey;

    // Shared reusable components
    private Credentials credentials;
    private TransactionManager transactionManager;

    public ContractService(
            ArtistSharesFactory artistSharesFactory,
            ArtistRepository artistRepository,
            Web3j web3j,
            @Value("${web3j.private-key}") String privateKey) {
        this.artistSharesFactory = artistSharesFactory;
        this.artistRepository = artistRepository;
        this.web3j = web3j;
        this.privateKey = privateKey;
    }

    @PostConstruct
    public void init() {
        try {
            this.credentials = Credentials.create(privateKey);
            this.transactionManager = new FastRawTransactionManager(web3j, credentials);
            logger.info("Shared Web3j credentials and transaction manager initialized.");
        } catch (Exception e) {
            logger.error("Failed to initialize Web3j credentials", e);
            throw new IllegalStateException("Cannot initialize Web3j credentials", e);
        }
    }

    @Cacheable(value = "contractAddresses", key = "#artistId")
    public String getContractAddress(String artistId) {
        logger.info("Fetching contract address for artistId: {}", artistId);
        try {
            Optional<String> contractAddressOpt = artistRepository.findContractAddressByArtistId(artistId);
            logger.info("DB lookup result for artistId {}: {}", artistId, contractAddressOpt.orElse("NULL"));
            if (contractAddressOpt.isPresent()) {
                String addr = contractAddressOpt.get();
                boolean isValid = isValidEthereumAddress(addr);
                logger.info("Validation for addr {}: valid={}, zero={}", addr, isValid, isZeroAddress(addr));
                if (isValid && !isZeroAddress(addr)) {
                    return addr;
                }
            }

            String address = artistSharesFactory.getTokenByArtistId(artistId).send();
            if (address == null || !isValidEthereumAddress(address) || isZeroAddress(address)) {
                logger.warn("No valid contract address from factory for artistId {}", artistId);
                return null;
            }

            artistRepository.updateContractAddress(artistId, address);
            evictPriceCaches(artistId); // Clear price/volume caches on new deployment
            logger.info("Deployed/updated contract for artistId {}: {}", artistId, address);
            return address;

        } catch (Exception e) {
            logger.error("Error fetching contract address for artistId {}: {}", artistId, e.getMessage(), e);
            return null;
        }
    }

    @Cacheable(value = "prices", key = "#artistId")
    public String getCurrentPrice(String artistId) {
        logger.debug("Fetching formatted price for artistId: {}", artistId);
        try {
            BigDecimal priceUsd = getCurrentPriceRaw(artistId);
            return formatUsd(priceUsd);
        } catch (Exception e) {
            logger.error("Error formatting price for artistId {}: {}", artistId, e.getMessage(), e);
            return "$0.00";
        }
    }

    @Cacheable(value = "pricesRaw", key = "#artistId")
    public BigDecimal getCurrentPriceRaw(String artistId) {
        logger.debug("Fetching raw price for artistId: {}", artistId);
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return new BigDecimal("0.00000050");
            }

            ArtistSharesToken token = loadTokenContract(contractAddress);

            BigInteger priceMicroUSD = token.getCurrentPriceMicroUSD().send();
            BigDecimal priceUsd = new BigDecimal(priceMicroUSD != null ? priceMicroUSD : BigInteger.valueOf(50))
                    .divide(new BigDecimal("100000000"), 10, RoundingMode.HALF_UP);
        
            logger.debug("Price for {}: {} microUSD → ${}", artistId, priceMicroUSD, priceUsd);
            return priceUsd;

        } catch (Exception e) {
            logger.error("Error fetching raw price for artistId {}: {}", artistId, e.getMessage(), e);
            return new BigDecimal("0.00000050"); // last-resort fallback
        }
    }

    @Cacheable(value = "volumes", key = "#artistId")
    public String getTotalVolumeTraded(String artistId) {
        logger.debug("Fetching formatted volume for artistId: {}", artistId);
        try {
            BigDecimal volumeUsd = getTotalVolumeTradedRaw(artistId);
            return formatUsd(volumeUsd);
        } catch (Exception e) {
            logger.error("Error formatting volume for artistId {}: {}", artistId, e.getMessage(), e);
            return "$0.00";
        }
    }

    @Cacheable(value = "volumesRaw", key = "#artistId")
    public BigDecimal getTotalVolumeTradedRaw(String artistId) {
        logger.debug("Fetching raw volume for artistId: {}", artistId);
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return BigDecimal.ZERO;
            }

            ArtistSharesToken token = loadTokenContract(contractAddress);
            BigInteger volumeWei = token.totalVolumeTraded().send();
            BigInteger ethUsdPrice = token.getEthUsdPrice().send();

            return convertWeiToUsd(volumeWei, ethUsdPrice);

        } catch (Exception e) {
            logger.error("Error fetching raw volume for artistId {}: {}", artistId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    public ArtistSharesToken loadTokenContract(String contractAddress) {
        if (contractAddress == null || !isValidEthereumAddress(contractAddress) || isZeroAddress(contractAddress)) {
            throw new IllegalArgumentException("Invalid contract address: " + contractAddress);
        }
        try {
            ArtistSharesToken token = ArtistSharesToken.load(
                    contractAddress, web3j, transactionManager, new DefaultGasProvider());
            logger.debug("Loaded token contract: {}", contractAddress);
            return token;
        } catch (Exception e) {
            logger.error("Failed to load token contract {}: {}", contractAddress, e.getMessage(), e);
            throw new RuntimeException("Failed to load token contract", e);
        }
    }

    private boolean isValidEthereumAddress(String address) {
        if (address == null) return false;
        boolean matches = ETHEREUM_ADDRESS_PATTERN.matcher(address).matches();
        if (!matches) {
            logger.warn("Invalid Ethereum address format: {}", address);
        }
        return matches;
    }

    private boolean isZeroAddress(String address) {
        boolean isZero = "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address)
                || "0x".equals(address) || address.isEmpty();
        if (isZero) {
            logger.debug("Zero or empty address detected: {}", address);
        }
        return isZero;
    }

    private BigDecimal convertWeiToUsd(BigInteger weiValue, BigInteger ethUsdPrice) {
        if (weiValue == null || weiValue.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigInteger effectiveEthUsd = (ethUsdPrice == null || ethUsdPrice.signum() == 0)
                ? FALLBACK_ETH_USD_PRICE
                : ethUsdPrice;

        BigDecimal eth = new BigDecimal(weiValue).divide(WEI_TO_ETH, 18, RoundingMode.HALF_UP);
        BigDecimal usdPerEth = new BigDecimal(effectiveEthUsd).divide(ETH_USD_SCALE, 8, RoundingMode.HALF_UP);
        return eth.multiply(usdPerEth);
    }

    private String formatUsd(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "$0.00";
        }

        BigDecimal abs = value.abs().setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();

        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            return "$" + abs.divide(BigDecimal.valueOf(1_000_000_000), 2, RoundingMode.HALF_UP) + "B";
        } else if (abs.compareTo(BigDecimal.valueOf(1_000_000)) >= 0) {
            return "$" + abs.divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP) + "M";
        } else if (abs.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            return "$" + abs.divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP) + "K";
        } else {
            return "$" + abs.max(BigDecimal.valueOf(0.00000001)).toPlainString();
        }
    }

    @CacheEvict(value = {"prices", "pricesRaw", "volumes", "volumesRaw"}, key = "#artistId")
    public void evictPriceCaches(String artistId) {
        logger.debug("Evicted price/volume caches for artistId: {}", artistId);
    }

    @CacheEvict(value = "contractAddresses", key = "#artistId")
    public void evictContractAddressCache(String artistId) {
        logger.info("Evicted contractAddresses cache for artistId: {}", artistId);
    }
}