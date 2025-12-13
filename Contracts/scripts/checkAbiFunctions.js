const fs = require('fs');
const path = require('path');

// Load the ABI file
const abiPath = path.join(__dirname, '../artifacts/contracts/ArtistSharesToken.sol/ArtistSharesToken.json');
const file = JSON.parse(fs.readFileSync(abiPath, 'utf8'));
const abi = file.abi;

// Function names to check for
const requiredFunctions = ['recordPrice', 'getPriceHistory', 'getLatestCalculatedPrice'];

const foundFunctions = abi
  .filter(item => item.type === 'function')
  .map(func => func.name);

requiredFunctions.forEach(funcName => {
  if (foundFunctions.includes(funcName)) {
    console.log(`✅ Found: ${funcName}`);
  } else {
    console.log(`❌ Missing: ${funcName}`);
  }
});