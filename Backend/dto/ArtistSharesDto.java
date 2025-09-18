package com.musicinvestment.musicapp.dto;

import com.musicinvestment.musicapp.model.Artist;

public class ArtistSharesDto {
    private String artistId;
    private String artistName;
    private String spotifyUrl;
    private String imageUrl;
    private int followers;
    private int popularity;
    private String contractAddress;

    // New fields for market data
    private double currentPrice;
    private double priceChangePercent;
    private int totalVolume;
    private int availableSupply;
    private int airdropSupply;

    public ArtistSharesDto() {}

    public ArtistSharesDto(String artistId, String artistName, String spotifyUrl,
                           String imageUrl, int followers, int popularity, String contractAddress,
                           double currentPrice, double priceChangePercent,
                           int totalVolume, int availableSupply, int airdropSupply) {
        this.artistId = artistId;
        this.artistName = artistName;
        this.spotifyUrl = spotifyUrl;
        this.imageUrl = imageUrl;
        this.followers = followers;
        this.popularity = popularity;
        this.contractAddress = contractAddress;
        this.currentPrice = currentPrice;
        this.priceChangePercent = priceChangePercent;
        this.totalVolume = totalVolume;
        this.availableSupply = availableSupply;
        this.airdropSupply = airdropSupply;
    }

    // Getters and Setters
    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getSpotifyUrl() {
        return spotifyUrl;
    }

    public void setSpotifyUrl(String spotifyUrl) {
        this.spotifyUrl = spotifyUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getFollowers() {
        return followers;
    }

    public void setFollowers(int followers) {
        this.followers = followers;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getPriceChangePercent() {
        return priceChangePercent;
    }

    public void setPriceChangePercent(double priceChangePercent) {
        this.priceChangePercent = priceChangePercent;
    }

    public int getTotalVolume() {
        return totalVolume;
    }

    public void setTotalVolume(int totalVolume) {
        this.totalVolume = totalVolume;
    }

    public int getAvailableSupply() {
        return availableSupply;
    }

    public void setAvailableSupply(int availableSupply) {
        this.availableSupply = availableSupply;
    }

    public int getAirdropSupply() {
        return airdropSupply;
    }

    public void setAirdropSupply(int airdropSupply) {
        this.airdropSupply = airdropSupply;
    }

    // Mapper method for creating DTO from Artist model
    public static ArtistSharesDto fromArtist(Artist artist) {
        return new ArtistSharesDto(
            artist.getId(),
            artist.getName(),
            artist.getSpotifyUrl(),
            artist.getImageUrl(),
            artist.getFollowers(),
            artist.getPopularity(),
            artist.getContractAddress(),
            artist.getCurrentPrice() != null ? artist.getCurrentPrice() : 0.0,
            artist.getPriceChangePercent() != null ? artist.getPriceChangePercent() : 0.0,
            artist.getTotalVolume() != null ? artist.getTotalVolume() : 0,
            artist.getAvailableSupply() != null ? artist.getAvailableSupply() : 0,
            artist.getAirdropSupply() != null ? artist.getAirdropSupply() : 0
        );
    }
}