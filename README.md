# Sounquity
A decentralized platform for artist discovery and token trading, blending Spotify-inspired UI with blockchain marketplaces.

## Overview
Sounquity is a full-stack Dapp that enables users to discover artists, connect wallets (MetaMask, WalletConnect), and trade artist tokens on Ethereum. It integrates a React/Vite frontend, Spring Boot backend, and Solidity smart contracts.

## Tech Stack
- *Frontend*: React, Vite, Reown AppKit, Web3.js
- *Backend*: Spring Boot, Java, Web3j (blockchain event syncing)
- *Smart Contracts*: Solidity, Hardhat (token deployment and factory)
- *APIs*: REST endpoints for artist search, wallet login, trade syncing
- *Database*: Configured for PostgreSQL (not included in repo)

## Key Features
- Spotify-like artist discovery (search, artist cards)
- Wallet integration for token trading
- Real-time trade syncing via blockchain event listeners
- Dynamic Bonding Curve: Token price adjusts with supply and demand until liquidity migrates to Uniswap.
-	Oracle-Pegged Pricing: Real-time ETH/USD feed from Chainlink ensures fair valuations.
-	Built-in Airdrop & Vesting: Automated distribution and time-based unlocking for artists, users, and the platform.
-	Fee & Limit Controls: Tiered buy/sell fees, daily trade caps, and anti-dump mechanisms maintain stability.
-	On-Chain Candlestick Data: Price and volume history stored directly on-chain for live charts.
-	Automatic Uniswap Integration: Liquidity seamlessly transitions to Uniswap once curve thresholds are met.
-		Secure & Modular: Uses OpenZeppelin standards, Uniswap v2 libraries, and Chainlink data feeds

## Project Structure
- backend/: Spring Boot APIs and blockchain sync (e.g., BlockchainSyncService)
- frontend/: React/Vite UI with wallet integration (e.g., ConnectWallet.jsx)
- contracts/: Solidity contracts (ArtistSharesToken.sol, ArtistSharesFactory.sol)

## Setup Instructions
1. *Backend*:
   - cd backend
   - Configure src/main/resources/application.properties with DB and RPC
   - Run mvn spring-boot:run
2. *Frontend*:
   - cd frontend
   - Install: npm install
   - Run: npm run dev
3. *Contracts*:
   - cd contracts
   - Compile: npx hardhat compile
   - Deploy: npx hardhat run scripts/deploy.js --network localhost

## Status
In development, with core features (artist search, wallet connect, token trading) functional. Not yet deployed live.

## License

MIT License
