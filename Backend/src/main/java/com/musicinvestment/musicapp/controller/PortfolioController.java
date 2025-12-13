package com.musicinvestment.musicapp.controller;

import com.musicinvestment.musicapp.model.Portfolio;
import com.musicinvestment.musicapp.service.PortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/portfolio")
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

    @GetMapping("/{userId}")
    public List<Portfolio> getPortfolioByUserId(@PathVariable String userId) {
        return portfolioService.getPortfolioByUserId(userId);
    }
}
