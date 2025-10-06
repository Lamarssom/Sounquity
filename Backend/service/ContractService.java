package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import com.musicinvestment.musicapp.contract.ArtistSharesToken;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ContractService {
    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);
    private static final Pattern ETHEREUM_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final BigDecimal WEI_TO_ETH = new BigDecimal("1000000000000000000"); // 10^18
    private static final BigDecimal ETH_USD_SCALE = new BigDecimal("100000000"); // 10^8
    private static final BigDecimal FALLBACK_PRICE = new BigDecimal("3.50"); // Use $3.50 for testing

    private final ArtistSharesFactory artistSharesFactory;
    private final ArtistRepository artistRepository;
    private final Web3j web3j;
    private final String privateKey;

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

    @Cacheable(value = "contractAddresses", key = "#artistId")
    public String getContractAddress(String artistId) {
        logger.info("Fetching contract address for artistId: {}", artistId);
        try {
            Optional<String> contractAddress = artistRepository.findContractAddressByArtistId(artistId);
            if (contractAddress.isPresent() && isValidEthereumAddress(contractAddress.get())) {
                logger.debug("Found contract address in repository for artistId {}: {}", artistId, contractAddress.get());
                return contractAddress.get();
            }
            String address = artistSharesFactory.getTokenByArtistId(artistId).send();
            logger.debug("Fetched contract address from factory for artistId {}: {}", artistId, address);
            if (address == null || address.isEmpty() || address.equals("0x") || !isValidEthereumAddress(address)) {
                logger.warn("No valid contract address found for artistId {} in factory", artistId);
                return null;
            }
            artistRepository.updateContractAddress(artistId, address);
            logger.info("Updated contract address for artistId {}: {}", artistId, address);
            return address;
        } catch (Exception e) {
            logger.error("Error fetching contract address for artistId {}: {}", artistId, e.getMessage(), e);
            return null;
        }
    }

    @Cacheable(value = "prices", key = "#artistId")
    public String getCurrentPrice(String artistId) {
        logger.debug("Fetching price for artistId: {}", artistId);
        try {
            BigDecimal priceUsd = getCurrentPriceRaw(artistId);
            String result = formatUsd(priceUsd);
            logger.debug("Cached price for artistId {}: {}", artistId, result);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching price for artistId {}: {}", artistId, e.getMessage());
            return "$0.00";
        }
    }

    @Cacheable(value = "prices", key = "#artistId + '-raw'")
    public BigDecimal getCurrentPriceRaw(String artistId) {
        logger.debug("Fetching raw price for artistId: {}", artistId);
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return FALLBACK_PRICE;
            }
            ArtistSharesToken token = loadTokenContract(contractAddress);
            BigInteger priceWei = token.getCurrentPrice().send();
            logger.debug("Raw price (wei) for artistId {}: {}", artistId, priceWei);
            BigInteger ethUsdPrice = token.getEthUsdPrice().send();
            logger.debug("ETH/USD price for artistId {}: {}", artistId, ethUsdPrice);
            BigDecimal priceUsd = convertWeiToUsd(priceWei, ethUsdPrice);
            if (priceUsd == null || priceUsd.compareTo(BigDecimal.ZERO) <= 0) {
                logger.warn("Zero or negative price for artistId {}, using fallback: {}", artistId, FALLBACK_PRICE);
                return FALLBACK_PRICE;
            }
            logger.debug("Cached raw price for artistId {}: {}", artistId, priceUsd);
            return priceUsd;
        } catch (Exception e) {
            logger.error("Error fetching raw price for artistId {}: {}, using fallback", artistId, e.getMessage());
            return FALLBACK_PRICE;
        }
    }

    @Cacheable(value = "volumes", key = "#artistId")
    public String getTotalVolumeTraded(String artistId) {
        logger.debug("Fetching volume for artistId: {}", artistId);
        try {
            BigDecimal volumeUsd = getTotalVolumeTradedRaw(artistId);
            String result = formatUsd(volumeUsd);
            logger.debug("Cached volume for artistId {}: {}", artistId, result);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching volume for artistId {}: {}", artistId, e.getMessage());
            return "$0.00";
        }
    }

    @Cacheable(value = "volumes", key = "#artistId + '-raw'")
    public BigDecimal getTotalVolumeTradedRaw(String artistId) {
        logger.debug("Fetching raw volume for artistId: {}", artistId);
        try {
            String contractAddress = getContractAddress(artistId);
            if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
                logger.warn("No valid contract address for artistId: {}", artistId);
                return BigDecimal.ZERO;
            }
            ArtistSharesToken token = loadTokenContract(contractAddress);
            BigInteger volumeWei = token.getTotalVolumeTraded().send();
            logger.debug("Raw volume (wei) for artistId {}: {}", artistId, volumeWei);
            BigInteger ethUsdPrice = token.getEthUsdPrice().send();
            logger.debug("ETH/USD price for artistId {}: {}", artistId, ethUsdPrice);
            BigDecimal volumeUsd = convertWeiToUsd(volumeWei, ethUsdPrice);
            logger.debug("Cached raw volume for artistId {}: {}", artistId, volumeUsd);
            return volumeUsd;
        } catch (Exception e) {
            logger.error("Error fetching raw volume for artistId {}: {}, returning zero", artistId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public ArtistSharesToken loadTokenContract(String contractAddress) {
        if (contractAddress == null || !isValidEthereumAddress(contractAddress)) {
            logger.error("Invalid contract address: {}", contractAddress);
            throw new IllegalArgumentException("Invalid contract address: " + contractAddress);
        }
        try {
            Credentials credentials = Credentials.create(privateKey);
            logger.debug("Created credentials for privateKey: {}", privateKey.substring(0, 6) + "...");
            TransactionManager txManager = new FastRawTransactionManager(web3j, credentials);
            ArtistSharesToken token = ArtistSharesToken.load(contractAddress, web3j, txManager, new DefaultGasProvider());
            logger.debug("Loaded token contract for address: {}", contractAddress);
            return token;
        } catch (Exception e) {
            logger.error("Failed to load token contract for address {}: {}", contractAddress, e.getMessage(), e);
            throw new RuntimeException("Failed to load token contract: " + e.getMessage(), e);
        }
    }

    private boolean isValidEthereumAddress(String address) {
        boolean valid = address != null && ETHEREUM_ADDRESS_PATTERN.matcher(address).matches();
        if (!valid) {
            logger.warn("Invalid Ethereum address: {}", address);
        }
        return valid;
    }

    private BigDecimal convertWeiToUsd(BigInteger weiValue, BigInteger ethUsdPrice) {
        if (weiValue == null || weiValue.equals(BigInteger.ZERO)) {
            logger.debug("Zero wei value in convertWeiToUsd, returning 0");
            return BigDecimal.ZERO;
        }
        if (ethUsdPrice == null || ethUsdPrice.equals(BigInteger.ZERO)) {
            logger.warn("Zero ETH/USD price in convertWeiToUsd, using fallback 3500");
            ethUsdPrice = BigInteger.valueOf(350000000000L); // $3500 * 10^8
        }
        BigDecimal weiToEth = new BigDecimal(weiValue).divide(WEI_TO_ETH, 18, RoundingMode.HALF_UP);
        BigDecimal usdPrice = new BigDecimal(ethUsdPrice).divide(ETH_USD_SCALE, 8, RoundingMode.HALF_UP);
        BigDecimal result = weiToEth.multiply(usdPrice);
        logger.debug("Converted wei={} to USD={}", weiValue, result);
        return result;
    }

    private String formatUsd(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Formatting USD value as $0.00: {}", value);
            return "$0.00";
        }
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
}
