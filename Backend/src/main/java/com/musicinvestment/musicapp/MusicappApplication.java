package com.musicinvestment.musicapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling // Enables scheduled tasks like auto-refreshing the Spotify token
public class MusicappApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicappApplication.class, args);
    }

}