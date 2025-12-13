package com.musicinvestment.musicapp.repository;

import com.musicinvestment.musicapp.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, String> {
    List<Portfolio> findByUserId(String userId);
}
