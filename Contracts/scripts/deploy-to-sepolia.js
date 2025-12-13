async function main() {
  const [deployer] = await ethers.getSigners();

  console.log("Deploying with account:", deployer.address);
  console.log("Balance:", ethers.utils.formatEther(await deployer.getBalance()), "ETH");

  // Real Sepolia addresses (confirmed working Dec 2025)
  const PRICE_FEED = "0x694AA1769357215DE4FAC081bf1f309aDC325306";     // Chainlink ETH/USD
  const UNISWAP_ROUTER = "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"; // Uniswap V2 Router (works on Sepolia)

  // Deploy Factory
  const Factory = await ethers.getContractFactory("ArtistSharesFactory");
  const factory = await Factory.deploy(
    deployer.address,     // platform wallet
    PRICE_FEED,
    UNISWAP_ROUTER
  );

  await factory.deployed();  // Wait for deploy
  console.log("ArtistSharesFactory deployed →", factory.address);  // ← NO parentheses!

  // Create The Weeknd token
  const tx = await factory.createArtistToken(
    "theweeknd",
    "The Weeknd Shares",
    "WEEKND",
    deployer.address
  );

  const receipt = await tx.wait();
  // Safer event parsing for v5
  const event = receipt.events?.find(e => e.event === "ArtistTokenCreated") || receipt.events[0];
  const tokenAddress = event.args.tokenAddress;

  console.log("The Weeknd token created →", tokenAddress);
  console.log("\nVerify on Etherscan:");
  console.log(`https://sepolia.etherscan.io/address/${factory.address}#code`);
  console.log(`https://sepolia.etherscan.io/address/${tokenAddress}#code`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});