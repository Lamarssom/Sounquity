const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("ArtistSharesToken", function () {
    let Token, artistSharesToken, owner, addr1, addr2, platformAddress;

    beforeEach(async function () {
        console.log("Starting deployment...");
        Token = await ethers.getContractFactory("ArtistSharesToken");
        [owner, addr1, addr2, platformAddress] = await ethers.getSigners(); // Get platformAddress as well

        // Deploying with a supply of 1 billion tokens (1,000,000,000 * 10^18)
        const initialSupply = ethers.utils.parseUnits("1000000000", 18); // 1 billion tokens
        const basePrice = ethers.utils.parseUnits("1", 18); // Set a base price for the shares

        // Deploy with the platformAddress included
        artistSharesToken = await Token.deploy("ArtistShares", "AST", initialSupply, basePrice, platformAddress.address);
        await artistSharesToken.deployed(); // Ensure it's deployed

        console.log("Contract deployed at:", artistSharesToken.address);
    });

    it("Should assign the correct share to the owner, platform, and airdrop", async function () {
        const totalSupply = await artistSharesToken.totalSupply();

        const ownerBalance = await artistSharesToken.balanceOf(owner.address);
        const platformSupply = await artistSharesToken.platformSupply(); // Corrected
        const airdropSupply = await artistSharesToken.airdropSupply(); // Corrected

        const expectedOwnerBalance = totalSupply.mul(10).div(100); // 10% for owner
        const expectedPlatformBalance = totalSupply.mul(5).div(100); // 5% for platform
        const expectedAirdropBalance = totalSupply.mul(5).div(100); // 5% for airdrop

        console.log("Owner Balance:", ownerBalance.toString());
        console.log("Platform Balance:", platformSupply.toString());
        console.log("Airdrop Balance:", airdropSupply.toString());

        // ✅ Ensure owner gets 10%, platform gets 5%, airdrop gets 5%
        expect(ownerBalance.toString()).to.equal(expectedOwnerBalance.toString());
        expect(platformSupply.toString()).to.equal(expectedPlatformBalance.toString());
        expect(airdropSupply.toString()).to.equal(expectedAirdropBalance.toString());
    });

    it("Should assign 80% of the total supply for user trading", async function () {
        const totalSupply = await artistSharesToken.totalSupply();
        const userTradingSupply = await artistSharesToken.userTradingSupply(); // Corrected

        const expectedUserTradingBalance = totalSupply.mul(80).div(100); // 80% for user trading

        console.log("User Trading Balance:", userTradingSupply.toString());

        // ✅ Ensure 80% goes for user trading
        expect(userTradingSupply.toString()).to.equal(expectedUserTradingBalance.toString());
    });

    it("Should set the correct base price and price limit", async function () {
        const basePrice = await artistSharesToken.basePrice();
        const priceLimit = await artistSharesToken.priceLimit();

        // Check if price limit is 5% of base price
        const expectedPriceLimit = basePrice.mul(5).div(100);

        console.log("Base Price:", basePrice.toString());
        console.log("Price Limit:", priceLimit.toString());

        // ✅ Ensure base price and price limit are correctly set
        expect(basePrice.toString()).to.equal(ethers.utils.parseUnits("1", 18).toString()); // Expect base price of 1
        expect(priceLimit.toString()).to.equal(expectedPriceLimit.toString()); // 5% of base price as limit
    });

    it("Should distribute airdrop correctly", async function () {
        const airdropAmount = ethers.utils.parseUnits("100", 18); // 100 tokens for the airdrop
        await artistSharesToken.distributeAirdrop(addr1.address, airdropAmount);

        const addr1Balance = await artistSharesToken.balanceOf(addr1.address);
        const remainingAirdropSupply = await artistSharesToken.airdropSupply();

        console.log("Addr1 Balance:", addr1Balance.toString());
        console.log("Remaining Airdrop Supply:", remainingAirdropSupply.toString());

        // The expected remaining supply should be 5% of the total supply minus the airdropAmount distributed
        const totalSupply = await artistSharesToken.totalSupply();
        const expectedRemainingAirdropSupply = totalSupply.mul(5).div(100).sub(airdropAmount); // 5% of total supply minus airdrop distributed

        // ✅ Ensure addr1 received the correct airdrop amount
        expect(addr1Balance.toString()).to.equal(airdropAmount.toString());
        expect(remainingAirdropSupply.toString()).to.equal(expectedRemainingAirdropSupply.toString()); // Adjusted expected value
    });
});