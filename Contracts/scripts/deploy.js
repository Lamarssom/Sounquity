require("dotenv").config(); // Load environment variables
const hre = require("hardhat");

async function main() {
  try {
    const [deployer] = await hre.ethers.getSigners(); // Get deployer account
    console.log("Deploying contracts with the account:", deployer.address);

    // Get contract factory
    const ArtistSharesToken = await hre.ethers.getContractFactory("ArtistSharesToken");

    // Set the initial parameters for the contract
    const name = "ArtistShares";
    const symbol = "AST";
    const initialSupply = hre.ethers.parseUnits("1000000", 18); // 1M tokens
    const basePrice = hre.ethers.parseUnits("1", 18); // Set the base price of 1 token (adjust as needed)
    const platformAddress = "0xd427d6782F66C62a3992Ca4fA41fF3BBc13C8579"; // Replace with the actual platform address

    // Deploy the contract with the required constructor arguments
    const artistSharesToken = await ArtistSharesToken.deploy(
      name,
      symbol,
      initialSupply,
      basePrice, // Pass the base price as an argument
      platformAddress, // Pass platform address as an argument
      {
        gasLimit: 3000000, // Adjust gas limit if needed
      }
    );

    console.log("Waiting for contract deployment...");
    await artistSharesToken.waitForDeployment(); // Wait for deployment to complete

    console.log(`ArtistSharesToken deployed to: ${await artistSharesToken.getAddress()}`);
  } catch (error) {
    console.error("Deployment failed:", error);
    process.exit(1);
  }
}

main();