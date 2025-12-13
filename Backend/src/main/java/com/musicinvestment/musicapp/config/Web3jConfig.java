package com.musicinvestment.musicapp.config;

import com.musicinvestment.musicapp.contract.ArtistSharesFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

@Configuration
public class Web3jConfig {

    @Value("${web3j.node-url}")
    private String nodeUrl;

    @Value("${web3j.private-key}")
    private String privateKey;

    @Value("${contract.artist-shares-factory-address}")
    private String factoryAddress;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(nodeUrl));
    }

    @Bean
    public ArtistSharesFactory artistSharesFactory(Web3j web3j) {
        Credentials credentials = Credentials.create(privateKey);
        TransactionManager txManager = new FastRawTransactionManager(web3j, credentials);
        return ArtistSharesFactory.load(factoryAddress, web3j, txManager, new DefaultGasProvider());
    }
}