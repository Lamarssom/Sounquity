package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.ShareOwnership;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.repository.ShareOwnershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ShareOwnershipService {

    @Autowired
    private ShareOwnershipRepository shareOwnershipRepository;

    public void allocateShares(String userId, String artistId, int shares) {
        if (userId == null || artistId == null || shares <= 0) {
            throw new IllegalArgumentException("Invalid allocation request.");
        }

        User user = new User();
        user.setId(userId);

        Artist artist = new Artist();
        artist.setId(artistId);

        Optional<ShareOwnership> existingShares = shareOwnershipRepository.findByUserAndArtist(user, artist);

        if (existingShares.isPresent()) {
            ShareOwnership shareOwnership = existingShares.get();
            shareOwnership.setSharesOwned(shareOwnership.getSharesOwned() + shares);
            shareOwnershipRepository.save(shareOwnership);
        } else {
            ShareOwnership newShare = new ShareOwnership(user, artist, shares);
            shareOwnershipRepository.save(newShare);
        }
    }

    public int getSharesOwned(String userId, String artistId) {
        if (userId == null || artistId == null) {
            throw new IllegalArgumentException("Invalid request.");
        }

        User user = new User();
        user.setId(userId);

        Artist artist = new Artist();
        artist.setId(artistId);

        return shareOwnershipRepository.findByUserAndArtist(user, artist)
                .map(ShareOwnership::getSharesOwned)
                .orElse(0);
    }

    public List<ShareOwnership> getUserShares(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Invalid user ID.");
        }
        return shareOwnershipRepository.findByUserId(userId);
    }

    @Transactional
    public String transferShares(String senderId, String receiverId, String artistId, int shares) {
        if (senderId == null || receiverId == null || artistId == null || shares <= 0) {
            return "Invalid transfer request.";
        }

        User sender = new User();
        sender.setId(senderId);

        User receiver = new User();
        receiver.setId(receiverId);

        Artist artist = new Artist();
        artist.setId(artistId);

        Optional<ShareOwnership> senderOwnership = shareOwnershipRepository.findByUserAndArtist(sender, artist);

        if (senderOwnership.isEmpty() || senderOwnership.get().getSharesOwned() < shares) {
            return "Transfer failed: Insufficient shares.";
        }

        ShareOwnership senderShares = senderOwnership.get();
        senderShares.setSharesOwned(senderShares.getSharesOwned() - shares);

        Optional<ShareOwnership> receiverOwnership = shareOwnershipRepository.findByUserAndArtist(receiver, artist);
        if (receiverOwnership.isPresent()) {
            ShareOwnership receiverShares = receiverOwnership.get();
            receiverShares.setSharesOwned(receiverShares.getSharesOwned() + shares);
            shareOwnershipRepository.save(receiverShares);
        } else {
            ShareOwnership newShare = new ShareOwnership(receiver, artist, shares);
            shareOwnershipRepository.save(newShare);
        }

        shareOwnershipRepository.save(senderShares);
        return "Shares transferred successfully!";
    }

    @Transactional
    public String sellShares(String sellerId, String artistId, int shares, String buyerId) {
        if (sellerId == null || buyerId == null || artistId == null || shares <= 0) {
            return "Invalid sell request.";
        }

        User seller = new User();
        seller.setId(sellerId);

        User buyer = new User();
        buyer.setId(buyerId);

        Artist artist = new Artist();
        artist.setId(artistId);

        Optional<ShareOwnership> sellerOwnership = shareOwnershipRepository.findByUserAndArtist(seller, artist);

        if (sellerOwnership.isEmpty() || sellerOwnership.get().getSharesOwned() < shares) {
            return "Sale failed: Seller does not have enough shares.";
        }

        ShareOwnership sellerShares = sellerOwnership.get();
        sellerShares.setSharesOwned(sellerShares.getSharesOwned() - shares);

        Optional<ShareOwnership> buyerOwnership = shareOwnershipRepository.findByUserAndArtist(buyer, artist);
        if (buyerOwnership.isPresent()) {
            ShareOwnership buyerShares = buyerOwnership.get();
            buyerShares.setSharesOwned(buyerShares.getSharesOwned() + shares);
            shareOwnershipRepository.save(buyerShares);
        } else {
            ShareOwnership newShare = new ShareOwnership(buyer, artist, shares);
            shareOwnershipRepository.save(newShare);
        }

        shareOwnershipRepository.save(sellerShares);
        return "Shares sold successfully!";
    }
}
