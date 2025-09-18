package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.TradeOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOfferRepository extends JpaRepository<TradeOffer, String> {
    List<TradeOffer> findByArtistIdAndIsActiveTrue(String artistId);
    List<TradeOffer> findBySellerIdAndIsActiveTrue(String sellerId);
    List<TradeOffer> findByIsActiveTrue(); // New method to fetch all active trade offers
}