package com.musicinvestment.musicapp.dto;

public class ArtistDto {
    private String id;
    private String name;
    private String externalUrl;
    private int followers;
    private int popularity;
    private String imageUrl;
  
    public ArtistDto() {}

    public ArtistDto(String id, String name, String externalUrl, int followers, int popularity, String imageUrl, String contractAddress) {
        this.id = id;
        this.name = name;
        this.externalUrl = externalUrl;
        this.followers = followers;
        this.popularity = popularity;
        this.imageUrl = imageUrl;

    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    
}