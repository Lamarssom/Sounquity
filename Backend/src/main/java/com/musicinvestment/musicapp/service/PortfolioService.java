package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.Portfolio;
import com.musicinvestment.musicapp.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public List<Portfolio> getPortfolioByUserId(String userId) {
        return portfolioRepository.findByUserId(userId);
    }
}