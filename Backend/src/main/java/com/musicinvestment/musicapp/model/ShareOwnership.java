package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "share_ownership")
public class ShareOwnership {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(nullable = false)
    private int sharesOwned;

    public ShareOwnership() {}

    public ShareOwnership(User user, Artist artist, int sharesOwned) {
        this.user = user;
        this.artist = artist;
        this.sharesOwned = sharesOwned;
    }
}
