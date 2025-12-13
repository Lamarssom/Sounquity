const { ethers } = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with account:", deployer.address);

  // Deploy MockV3Aggregator (for testing price feed)
  const MockV3Aggregator = await ethers.getContractFactory("MockV3Aggregator");
  const mockAggregator = await MockV3Aggregator.deploy(8, 3500 * 1e8);
  await mockAggregator.deployed();
  console.log("MockV3Aggregator deployed to:", mockAggregator.address);

  // Deploy MockUniswapV2Factory
  const MockUniswapV2Factory = await ethers.getContractFactory("MockUniswapV2Factory");
  const mockFactory = await MockUniswapV2Factory.deploy();
  await mockFactory.deployed();
  console.log("MockUniswapV2Factory deployed to:", mockFactory.address);

  // Deploy MockUniswapV2Router
  const MockUniswapV2Router = await ethers.getContractFactory("MockUniswapV2Router");
  const mockRouter = await MockUniswapV2Router.deploy(mockFactory.address);
  await mockRouter.deployed();
  console.log("MockUniswapV2Router deployed to:", mockRouter.address);

  // Define constructor arguments
  const platformAddress = deployer.address;
  const priceFeedAddress = mockAggregator.address;
  const uniswapRouterAddress = mockRouter.address; // Use mock router address

  // Deploy ArtistSharesFactory
  const ArtistSharesFactory = await ethers.getContractFactory("ArtistSharesFactory");
  const factory = await ArtistSharesFactory.deploy(
    platformAddress,
    priceFeedAddress,
    uniswapRouterAddress
  );
  await factory.deployed();
  console.log("ArtistSharesFactory deployed to:", factory.address);

  // Write address to frontend config file
  const configPath = path.resolve(__dirname, "../../music-investment-frontend/src/utilities/config.js");
  const configContent = `export const FACTORY_CONTRACT_ADDRESS = "${factory.address}";\n`;
  fs.writeFileSync(configPath, configContent);
  console.log("Updated frontend config at:", configPath);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });