const { ethers } = require("hardhat");

async function main() {
  // Get the first Hardhat test account
  const [signer] = await ethers.getSigners();
  console.log("Signing with account:", signer.address);

  // Define the EIP-191 message (matches backend in UserService.java)
  const message = `Sign this message to authenticate with Sounquity at ${new Date().toISOString()}`;

  // Sign the message
  const signature = await signer.signMessage(message);

  console.log("Wallet Address:", signer.address);
  console.log("Message:", message);
  console.log("Signature:", signature);

  // Verify signature
  const messageHash = ethers.utils.hashMessage(message);
  const recoveredAddress = ethers.utils.verifyMessage(message, signature);
  console.log("Message Hash:", messageHash);
  console.log("Recovered Address:", recoveredAddress);
  console.log("Signature Valid:", recoveredAddress.toLowerCase() === signer.address.toLowerCase());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });