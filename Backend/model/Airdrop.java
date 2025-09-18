package com.musicinvestment.musicapp.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "airdrops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Airdrop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(name = "shares_received", nullable = false)
    private int sharesReceived;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructor without ID (used for new entries)
    public Airdrop(User user, Artist artist, int sharesReceived) {
        this.user = user;
        this.artist = artist;
        this.sharesReceived = sharesReceived;
        this.createdAt = LocalDateTime.now();
    }
}