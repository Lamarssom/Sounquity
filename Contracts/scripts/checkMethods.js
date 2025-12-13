const { Web3 } = require("web3");
const abi = require("../artifacts/contracts/ArtistSharesToken.sol/ArtistSharesToken.json").abi;
const web3 = new Web3();
const contract = new web3.eth.Contract(abi, "0x0000000000000000000000000000000000000000");

console.log("✅ Contract Methods:");
console.log(Object.keys(contract.methods).filter(m => !m.includes("0x")));