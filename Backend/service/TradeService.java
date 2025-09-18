package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.*;
import com.musicinvestment.musicapp.repository.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
public class TradeService {
    private static final Logger logger = LoggerFactory.getLogger(TradeService.class);

    private final ArtistStockRepository artistStockRepository;
    private final UserSharesRepository userSharesRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final TradeOfferRepository tradeOfferRepository;
    private final UserRepository userRepository;

    private static final int DAILY_LIMIT = 50; // Maximum number of shares a user can buy/sell per day

    public TradeService(ArtistStockRepository artistStockRepository,
                        UserSharesRepository userSharesRepository,
                        TradeHistoryRepository tradeHistoryRepository,
                        TradeOfferRepository tradeOfferRepository,
                        UserRepository userRepository) {
        this.artistStockRepository = artistStockRepository;
        this.userSharesRepository = userSharesRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
        this.tradeOfferRepository = tradeOfferRepository;
        this.userRepository = userRepository;
    }

    // Helper method to check if the user has exceeded the daily limit
    private boolean hasExceededDailyLimit(String userId, int shares, TradeType tradeType) {
        Timestamp today = new Timestamp(System.currentTimeMillis());
        List<TradeHistory> tradeHistories = tradeHistoryRepository.findByUserIdAndTradeTime(userId, today);
        int totalSharesTraded = tradeHistories.stream()
                .filter(trade -> trade.getTradeType() == tradeType)
                .mapToInt(TradeHistory::getShares)
                .sum();
        return totalSharesTraded + shares > DAILY_LIMIT;
    }

    // Method to get the opening price for the artist for the current day
    private BigDecimal getOpeningPriceForArtist(String artistId) {
        Timestamp startOfDay = ArtistStock.getStartOfDayTimestamp();
        Optional<ArtistStock> artistStockOpt = artistStockRepository.findByArtistIdAndDate(artistId, startOfDay);
        return artistStockOpt.map(ArtistStock::getStockPrice).orElse(BigDecimal.ZERO); // Updated to getStockPrice
    }

    // 1. List Shares for Sale
    public String listSharesForSale(String userId, String artistId, int shares, BigDecimal askingPrice) {
        Optional<UserShares> userSharesOpt = userSharesRepository.findByUserIdAndArtistId(userId, artistId);
        if (userSharesOpt.isEmpty()) {
            return "Error: You do not own any shares in this artist.";
        }

        UserShares userShares = userSharesOpt.get();
        if (userShares.getShares() < shares) {
            return "Error: You do not have enough shares to list for sale.";
        }

        // Check for price movement limit (5%)
        BigDecimal openingPrice = getOpeningPriceForArtist(artistId);
        if (openingPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxAllowedPrice = openingPrice.multiply(BigDecimal.valueOf(1.05)); // 5% increase
            BigDecimal minAllowedPrice = openingPrice.multiply(BigDecimal.valueOf(0.95)); // 5% decrease

            if (askingPrice.compareTo(maxAllowedPrice) > 0) {
                return "Error: The asking price is too high. It exceeds the 5% daily limit.";
            }
            if (askingPrice.compareTo(minAllowedPrice) < 0) {
                return "Error: The asking price is too low. It is below the 5% daily limit.";
            }
        }

        // Create a trade offer
        TradeOffer tradeOffer = new TradeOffer(userId, artistId, shares, askingPrice);
        tradeOfferRepository.save(tradeOffer);

        // Mark shares as listed
        userShares.setListedForSale(true);
        userShares.setAskingPrice(askingPrice);
        userSharesRepository.save(userShares);

        return "Shares listed for sale successfully!";
    }

    // 2. Buy Shares from a Listed Offer
    public String buyListedShares(String buyerId, String offerId, int sharesToBuy) {
        Optional<TradeOffer> tradeOfferOpt = tradeOfferRepository.findById(offerId);
        if (tradeOfferOpt.isEmpty() || !tradeOfferOpt.get().isActive()) {
            return "Error: This trade offer is no longer available.";
        }

        TradeOffer tradeOffer = tradeOfferOpt.get();
        if (sharesToBuy > tradeOffer.getShares()) {
            return "Error: Not enough shares available in this offer.";
        }

        BigDecimal totalCost = tradeOffer.getAskingPrice().multiply(BigDecimal.valueOf(sharesToBuy));
        Optional<User> buyerOpt = userRepository.findById(buyerId);
        if (buyerOpt.isEmpty()) {
            return "Error: Buyer account not found.";
        }

        User buyer = buyerOpt.get();
        if (buyer.getBalance().compareTo(totalCost) < 0) {
            return "Error: Insufficient balance.";
        }

        // Check for price movement limit (5%)
        BigDecimal openingPrice = getOpeningPriceForArtist(tradeOffer.getArtistId());
        if (openingPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxAllowedPrice = openingPrice.multiply(BigDecimal.valueOf(1.05)); // 5% increase
            BigDecimal minAllowedPrice = openingPrice.multiply(BigDecimal.valueOf(0.95)); // 5% decrease

            if (tradeOffer.getAskingPrice().compareTo(maxAllowedPrice) > 0) {
                return "Error: The price is too high. It exceeds the 5% daily limit.";
            }
            if (tradeOffer.getAskingPrice().compareTo(minAllowedPrice) < 0) {
                return "Error: The price is too low. It is below the 5% daily limit.";
            }
        }

        // Process the transaction: Deduct balance, add shares to buyer, update seller shares, etc.
        buyer.setBalance(buyer.getBalance().subtract(totalCost));
        userRepository.save(buyer);

        // Update buyer's shares
        Optional<UserShares> buyerSharesOpt = userSharesRepository.findByUserIdAndArtistId(buyerId, tradeOffer.getArtistId());
        UserShares buyerShares = buyerSharesOpt.orElse(new UserShares(buyerId, tradeOffer.getArtistId(), 0));
        buyerShares.setShares(buyerShares.getShares() + sharesToBuy);
        userSharesRepository.save(buyerShares);

        // Update seller's shares
        Optional<UserShares> sellerSharesOpt = userSharesRepository.findByUserIdAndArtistId(tradeOffer.getSellerId(), tradeOffer.getArtistId());
        if (sellerSharesOpt.isPresent()) {
            UserShares sellerShares = sellerSharesOpt.get();
            sellerShares.setShares(sellerShares.getShares() - sharesToBuy);
            if (sellerShares.getShares() == 0) {
                userSharesRepository.delete(sellerShares);
            } else {
                userSharesRepository.save(sellerShares);
            }
        }

        // Update trade offer
        if (sharesToBuy == tradeOffer.getShares()) {
            tradeOffer.setActive(false); // Mark offer as completed
        } else {
            tradeOffer.setShares(tradeOffer.getShares() - sharesToBuy);
        }
        tradeOfferRepository.save(tradeOffer);

        // Log trade history
        TradeHistory tradeHistory = new TradeHistory();
        tradeHistory.setUserId(buyerId);
        tradeHistory.setArtistId(tradeOffer.getArtistId());
        tradeHistory.setTradeType(TradeType.BUY);
        tradeHistory.setShares(sharesToBuy);
        tradeHistory.setPrice(tradeOffer.getAskingPrice());
        tradeHistoryRepository.save(tradeHistory);

        return "Trade successful! You bought " + sharesToBuy + " shares.";
    }

    // 3. Get Active Trade Offers for an Artist
    public List<TradeOffer> getActiveTradeOffers(String artistId) {
        return tradeOfferRepository.findByArtistIdAndIsActiveTrue(artistId);
    }
}