async function main() {
  const [deployer] = await ethers.getSigners();

  console.log("Deploying with account:", deployer.address);
  console.log("Balance:", ethers.utils.formatEther(await deployer.getBalance()), "ETH");

  // Deploy Mock Price Feed
  const MockV3Aggregator = await ethers.getContractFactory("MockV3Aggregator");
  const mockPriceFeed = await MockV3Aggregator.deploy(8, 3500 * 1e8); // ~$3500 ETH/USD
  await mockPriceFeed.deployed();
  console.log("Mock Price Feed deployed →", mockPriceFeed.address);

  // Deploy Mock Uniswap V2 Factory
  const MockUniswapFactory = await ethers.getContractFactory("MockUniswapV2Factory");
  const mockUniswapFactory = await MockUniswapFactory.deploy();
  await mockUniswapFactory.deployed();
  console.log("Mock Uniswap Factory deployed →", mockUniswapFactory.address);

  // Deploy Mock Uniswap V2 Router
  const MockRouter = await ethers.getContractFactory("MockUniswapV2Router");
  const mockRouter = await MockRouter.deploy(mockUniswapFactory.address);
  await mockRouter.deployed();
  console.log("Mock Uniswap Router deployed →", mockRouter.address);

  // Deploy ArtistSharesFactory with mocks
  const Factory = await ethers.getContractFactory("ArtistSharesFactory");
  const factory = await Factory.deploy(
    deployer.address,          // platform wallet
    mockPriceFeed.address,     // price feed
    mockRouter.address         // router
  );

  await factory.deployed();
  console.log("ArtistSharesFactory deployed →", factory.address);

  console.log("Deployment complete! Use Frontend to create tokens.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});