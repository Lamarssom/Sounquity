// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

contract ArtistSharesToken is ERC20, Ownable {
    AggregatorV3Interface internal pricefeed;
    uint256 public constant ETH_USD_DECIMALS = 8;
    
    // New constant for price increase
    uint256 public constant PRICE_INCREASE_PER_TOKEN = 10_000_000; // 0.00000001 ETH per share

    // Supplies
    uint256 public platformSupply;
    uint256 public airdropSupply;
    uint256 public userTradingSupply;

    // Trading limits and tracking
    uint256 public dailyLimit;
    mapping(address => uint256) public dailyTrades;
    uint256 public globalDailyTrades; // New: Global tracking

    // Price and platform details
    uint256 public basePrice;
    uint256 public priceLimit;
    uint256 public currentPrice;
    address public platformAddress;

    // Timeframe constants for candles
    uint256 public constant TF_1MIN = 60;
    uint256 public constant TF_5MIN = 300;
    uint256 public constant TF_15MIN = 900;
    uint256 public constant TF_1H = 3600;
    uint256 public constant TF_4H = 14400;
    uint256 public constant TF_1DAY = 86400;

    // Max candle counts per timeframe
    uint256 public constant MAX_CANDLES_1M = 1000;
    uint256 public constant MAX_CANDLES_5M = 800;
    uint256 public constant MAX_CANDLES_15M = 600;
    uint256 public constant MAX_CANDLES_1H = 500;
    uint256 public constant MAX_CANDLES_4H = 400;
    uint256 public constant MAX_CANDLES_1D = 365;

    // Price candle struct
    struct PriceCandle {
        uint256 timestamp;
        uint256 open;
        uint256 high;
        uint256 low;
        uint256 close;
        uint256 volume;
    }

    // Mapping candles by timeframe
    mapping(uint256 => PriceCandle[]) public candlesByTimeframe;

    // Other state variables
    uint256 public totalVolumeTraded;
    uint256 public lastRecordedDay;

    mapping(address => bool) public hasReceivedAirdrop;
    address[] public users;
    mapping(address => bool) public isUser;
    address public deployer;
    mapping(address => mapping(uint256 => uint256)) public sharesForSale;

    // Events
    event PriceUpdated(uint256 newPrice);
    event SharesListed(address indexed user, uint256 indexed shareId, uint256 price);
    event SharesBought(address indexed buyer, uint256 amount, uint256 price, uint256 timestamp);
    event SharesSold(address indexed seller, uint256 amount, uint256 price, uint256 timestamp);
    event CandleUpdated(uint256 timeframe, uint256 timestamp, uint256 open, uint256 high, uint256 low, uint256 close, uint256 volume);
    event DebugLog(string message, uint256 value1, uint256 value2, uint256 value3);
    event DailyLimitAdjusted(uint256 newDailyLimit, uint256 newTargetDailyLimitUsd, uint256 timestamp);

    constructor(
        string memory name,
        string memory symbol,
        uint256 initialSupply,
        uint256 _basePrice,
        address _platformAddress,
        address _teamWallet,
        uint256 popularity,
        address _priceFeedAddress
    ) ERC20(name, symbol) Ownable(msg.sender) {
        require(_basePrice > 0, "Base price must be greater than 0");
        require(popularity <= 100, "Popularity must be 0-100");

        deployer = msg.sender;
        platformAddress = _platformAddress;
        pricefeed = AggregatorV3Interface(_priceFeedAddress);
        currentPrice = _basePrice;

        uint256 artistAllocation = (initialSupply * 10) / 100;
        _mint(_teamWallet, artistAllocation);

        platformSupply = (initialSupply * 5) / 100;
        _mint(platformAddress, platformSupply);

        airdropSupply = (initialSupply * 5) / 100;
        _mint(address(this), airdropSupply);

        userTradingSupply = (initialSupply * 80) / 100;
        _mint(address(this), userTradingSupply);
        emit DebugLog("userTradingSupply initialized", userTradingSupply, initialSupply, block.timestamp);

        basePrice = _basePrice;
        priceLimit = (basePrice * (popularity >= 50 ? 10 : 5)) / 100;

        lastRecordedDay = block.timestamp / 1 days;
        uint256 ethUsdPrice = getEthUsdPrice();
        require(ethUsdPrice > 0, "Invalid ETH/USD price");
        uint256 targetDailyLimitUsd = 50_000 + (500 * popularity); // $50,000 + $500 per popularity point
        dailyLimit = (targetDailyLimitUsd * 1e18 * 1e8) / ethUsdPrice;
        emit DebugLog("dailyLimit set", dailyLimit, ethUsdPrice, block.timestamp);

        // Initialize candles for all timeframes
        uint256[6] memory timeframes = [TF_1MIN, TF_5MIN, TF_15MIN, TF_1H, TF_4H, TF_1DAY];
        uint256 initialPriceInUsd = (_basePrice * ethUsdPrice) / 10**18;
        require(initialPriceInUsd > 0, "Invalid initial USD price");
        emit DebugLog("Initial USD price", initialPriceInUsd, _basePrice, ethUsdPrice);
        for (uint256 i = 0; i < timeframes.length; i++) {
            uint256 timeframe = timeframes[i];
            uint256 roundedTimestamp = block.timestamp - (block.timestamp % timeframe);
            candlesByTimeframe[timeframe].push(PriceCandle({
                timestamp: roundedTimestamp,
                open: initialPriceInUsd,
                high: initialPriceInUsd,
                low: initialPriceInUsd,
                close: initialPriceInUsd,
                volume: 0
            }));
            emit CandleUpdated(
                timeframe,
                roundedTimestamp,
                initialPriceInUsd,
                initialPriceInUsd,
                initialPriceInUsd,
                initialPriceInUsd,
                0
            );
            emit DebugLog("Initial candle created", timeframe, roundedTimestamp, initialPriceInUsd);
        }
    }

    modifier onlyAuthorized() {
        require(msg.sender == owner() || msg.sender == platformAddress, "Not authorized");
        _;
    }

    // New: Dynamic adjustment function
    function adjustDailyLimit(uint256 newTargetDailyLimitUsd) public {
        require(msg.sender == platformAddress, "Only platform can adjust");
        uint256 ethUsdPrice = getEthUsdPrice();
        require(ethUsdPrice > 0, "Invalid ETH/USD price");
        require(newTargetDailyLimitUsd >= 10_000, "New limit too low"); // Minimum $10,000 USD
        dailyLimit = (newTargetDailyLimitUsd * 1e18 * 1e8) / ethUsdPrice;
        emit DailyLimitAdjusted(dailyLimit, newTargetDailyLimitUsd, block.timestamp);
        emit DebugLog("dailyLimit adjusted", dailyLimit, newTargetDailyLimitUsd, block.timestamp);
    }

    function getEthUsdPrice() public view returns (uint256) {
        (, int256 price, , , ) = pricefeed.latestRoundData();
        require(price > 0, "Invalid price from Chainlink");
        return uint256(price);
    }

    function getUsers() public view returns (address[] memory) {
        return users;
    }

    function _trackUser(address user) internal {
        if (!isUser[user]) {
            users.push(user);
            isUser[user] = true;
        }
    }

    function resetDailyTradesIfNewDay() internal {
        uint256 currentDay = block.timestamp / 1 days;
        if (currentDay > lastRecordedDay) {
            for (uint256 i = 0; i < users.length; i++) {
                dailyTrades[users[i]] = 0;
            }
            globalDailyTrades = 0; // Reset global trades
            lastRecordedDay = currentDay;
            emit DebugLog("Daily trades reset", currentDay, users.length, block.timestamp);
        }
    }

    function getCurrentPrice() public view returns (uint256) {
        return currentPrice;
    }

    function distributeAirdrop(address user, uint256 amount) public onlyOwner {
        require(amount <= airdropSupply, "Not enough airdrop tokens");
        require(!hasReceivedAirdrop[user], "Already received");
        _transfer(address(this), user, amount);
        hasReceivedAirdrop[user] = true;
        airdropSupply -= amount;
    }

    function buyShares(uint256 amount) public payable {
        emit DebugLog("Entered buyShares", amount, msg.value, block.timestamp);

        _trackUser(msg.sender);
        resetDailyTradesIfNewDay();

        _validateBuyShares(amount);
        _executeBuyTransferAndPayout(amount);
        _updateBuyPriceAndCandles(amount);

        emit DebugLog("Buy complete", amount, msg.sender.balance, block.timestamp);
        emit SharesBought(msg.sender, amount, currentPrice, block.timestamp);
    }

    function _validateBuyShares(uint256 amount) internal view {
        require(amount > 0, "Amount must be > 0");
        uint256 onChainPrice = currentPrice; // Inline getCurrentPrice
        require(onChainPrice > 0, "Price must be > 0");

        uint256 totalCost = onChainPrice * amount;
        require(msg.value >= totalCost, "Insufficient funds sent");

        uint256 contractBalance = balanceOf(address(this));
        require(contractBalance >= amount * 10**18, "[BUY] Not enough shares available");

        uint256 fee = (totalCost * 2) / 100;
        require(fee <= totalCost, "[BUY] Fee exceeds total cost");
        uint256 totalAfterFee = totalCost - fee;

        uint256 transactionLimit = dailyLimit * 15 / 100;
        uint256 maxTransactionLimit = (25_000 * 1e18 * 1e8) / getEthUsdPrice();
        transactionLimit = transactionLimit < maxTransactionLimit ? transactionLimit : maxTransactionLimit;
        require(totalAfterFee <= transactionLimit, "[BUY] Transaction exceeds per-trade limit");

        uint256 userCurrentBalance = balanceOf(msg.sender);
        uint256 maxHolding = (totalSupply() * 5) / 100;
        require(userCurrentBalance + (amount * 10**18) <= maxHolding, "[BUY] Holding limit exceeded");

        require(globalDailyTrades + totalAfterFee <= dailyLimit, "Exceeds global daily trade limit");
    }

    function _executeBuyTransferAndPayout(uint256 amount) internal {
        uint256 onChainPrice = currentPrice;
        uint256 totalCost = onChainPrice * amount;
        uint256 fee = (totalCost * 2) / 100;
        uint256 totalAfterFee = totalCost - fee;

        _transfer(address(this), msg.sender, amount * 10**18);
        emit DebugLog("After transfer - User balance", balanceOf(msg.sender), amount, block.timestamp);

        (bool sentFee, ) = payable(platformAddress).call{value: fee}("");
        require(sentFee, "[BUY] Fee transfer failed");

        dailyTrades[msg.sender] += totalAfterFee;
        globalDailyTrades += totalAfterFee;
        totalVolumeTraded += amount;

        if (msg.value > totalCost) {
            uint256 refund = msg.value - totalCost;
            (bool refundSent, ) = payable(msg.sender).call{value: refund}("");
            require(refundSent, "[BUY] Refund failed");
            emit DebugLog("Refund sent", refund, 0, block.timestamp);
        }
    }

    function _updateBuyPriceAndCandles(uint256 amount) internal {
        uint256 onChainPrice = currentPrice;
        uint256 priceIncrease = amount * PRICE_INCREASE_PER_TOKEN;
        uint256 newPrice = onChainPrice + priceIncrease;
        uint256 maxPriceChange = (onChainPrice * 5) / 100;
        if (newPrice > onChainPrice + maxPriceChange) newPrice = onChainPrice + maxPriceChange;
        if (newPrice > onChainPrice + priceLimit) newPrice = onChainPrice + priceLimit;
        currentPrice = newPrice;
        emit PriceUpdated(currentPrice);
        emit DebugLog("Price updated (wei)", onChainPrice, currentPrice, block.timestamp);

        uint256 ethUsdPrice = getEthUsdPrice();
        require(ethUsdPrice > 0, "Invalid ETH/USD price");
        uint256 priceInUsd = (currentPrice * ethUsdPrice) / 10**18;
        if (priceInUsd == 0) {
            priceInUsd = (basePrice * ethUsdPrice) / 10**18;
            emit DebugLog("PriceInUsd fallback to basePrice", priceInUsd, basePrice, block.timestamp);
        }
        emit DebugLog("Price in USD", priceInUsd, ethUsdPrice, block.timestamp);
        updateAllCandleHistories(priceInUsd, amount);
    }

    function sellShares(uint256 amount) public {
        emit DebugLog("== [SELL] Start ==", amount, 0, block.timestamp);

        _trackUser(msg.sender);
        resetDailyTradesIfNewDay();

        _validateSellShares(amount);
        _executeSellTransferAndPayout(amount);
        _updateSellPriceAndCandles(amount);

        emit DebugLog("== [SELL] Complete ==", amount, msg.sender.balance, block.timestamp);
        emit SharesSold(msg.sender, amount, currentPrice, block.timestamp);
    }

    function _validateSellShares(uint256 amount) internal view {
        require(amount > 0, "Amount must be > 0");
        uint256 onChainPrice = currentPrice; // Inline getCurrentPrice
        uint256 userBalance = balanceOf(msg.sender);
        require(userBalance >= amount * 10**18, "Not enough shares to sell");

        uint256 totalReturn = onChainPrice * amount;
        uint256 fee = (totalReturn * 2) / 100;
        require(fee <= totalReturn, "[SELL] Fee exceeds payout");
        uint256 payout = totalReturn - fee;
        require(address(this).balance >= payout, "Insufficient contract balance");

        uint256 transactionLimit = dailyLimit * 15 / 100;
        uint256 maxTransactionLimit = (25_000 * 1e18 * 1e8) / getEthUsdPrice();
        transactionLimit = transactionLimit < maxTransactionLimit ? transactionLimit : maxTransactionLimit;
        require(payout <= transactionLimit, "[SELL] Transaction exceeds per-trade limit");
        require(globalDailyTrades + payout <= dailyLimit, "Exceeds global daily trade limit");
    }

    function _executeSellTransferAndPayout(uint256 amount) internal {
        uint256 onChainPrice = currentPrice;
        uint256 totalReturn = onChainPrice * amount;
        uint256 fee = (totalReturn * 2) / 100;
        uint256 payout = totalReturn - fee;

        _transfer(msg.sender, address(this), amount * 10**18);
        emit DebugLog("After transfer - User balance", balanceOf(msg.sender), amount, block.timestamp);

        (bool sentFee, ) = platformAddress.call{value: fee}("");
        require(sentFee, "[SELL] Fee transfer failed");

        (bool sentPayout, ) = payable(msg.sender).call{value: payout}("");
        require(sentPayout, "[SELL] Payout failed");

        dailyTrades[msg.sender] += payout;
        globalDailyTrades += payout;
        totalVolumeTraded += amount;
    }

    function _updateSellPriceAndCandles(uint256 amount) internal {
        uint256 onChainPrice = currentPrice;
        uint256 priceDecrease = amount * PRICE_INCREASE_PER_TOKEN;
        uint256 newPrice = onChainPrice - priceDecrease;
        uint256 maxPriceChange = (onChainPrice * 5) / 100;
        if (newPrice < onChainPrice - maxPriceChange) newPrice = onChainPrice - maxPriceChange;
        if (newPrice < onChainPrice - priceLimit) newPrice = onChainPrice - priceLimit;
        if (newPrice < basePrice) newPrice = basePrice;
        currentPrice = newPrice;
        emit PriceUpdated(currentPrice);
        emit DebugLog("Price updated", onChainPrice, currentPrice, block.timestamp);

        uint256 ethUsdPrice = getEthUsdPrice();
        uint256 priceInUsd = (currentPrice * ethUsdPrice) / 10**18;
        updateAllCandleHistories(priceInUsd, amount);
    }

    function buyListedShares(address seller, uint256 shareId, uint256 amount) public payable {
        _trackUser(msg.sender);
        resetDailyTradesIfNewDay();

        uint256 price = sharesForSale[seller][shareId];
        uint256 total = price * amount;

        require(msg.value >= total, "Insufficient funds");
        require(balanceOf(seller) >= amount, "Seller lacks shares");

        uint256 fee = (total * 2) / 100;
        uint256 totalAfterFee = total - fee;
        uint256 transactionLimit = dailyLimit * 15 / 100;
        uint256 maxTransactionLimit = (25_000 * 1e18 * 1e8) / getEthUsdPrice();
        transactionLimit = transactionLimit < maxTransactionLimit ? transactionLimit : maxTransactionLimit;
        require(totalAfterFee <= transactionLimit, "[LISTED BUY] Transaction exceeds per-trade limit");
        require(globalDailyTrades + totalAfterFee <= dailyLimit, "Exceeds global daily trade limit");

        _transfer(seller, msg.sender, amount);
        (bool sentFee, ) = payable(platformAddress).call{value: fee}("");
        require(sentFee, "[LISTED BUY] Fee transfer failed");
        (bool sentPayout, ) = payable(seller).call{value: totalAfterFee}("");
        require(sentPayout, "[LISTED BUY] Payout failed");

        dailyTrades[msg.sender] += totalAfterFee;
        globalDailyTrades += totalAfterFee;
        totalVolumeTraded += amount;

        delete sharesForSale[seller][shareId];
        currentPrice = price;
        emit PriceUpdated(currentPrice);

        uint256 ethUsdPrice = getEthUsdPrice();
        uint256 priceInUsd = (currentPrice * ethUsdPrice) / 10**18;
        updateAllCandleHistories(priceInUsd, amount);
    }

    function resetDailyLimits(address user) public onlyOwner {
        dailyTrades[user] = 0;
    }

    function transfer(address recipient, uint256 amount) public override returns (bool) {
        uint256 fee = (amount * 2) / 100;
        uint256 afterFee = amount - fee;
        _transfer(msg.sender, recipient, afterFee);
        _transfer(msg.sender, owner(), fee);
        return true;
    }

    function transferFrom(address sender, address recipient, uint256 amount) public override returns (bool) {
        uint256 fee = (amount * 2) / 100;
        uint256 afterFee = amount - fee;
        _spendAllowance(sender, msg.sender, amount);
        _transfer(sender, recipient, afterFee);
        _transfer(sender, owner(), fee);
        return true;
    }

    function getTotalVolumeTraded() public view returns (uint256) {
        return totalVolumeTraded;
    }

    receive() external payable {}
    fallback() external payable {}

    function getCandleHistory(uint256 timeframe) public view returns (
        uint256[] memory timestamps,
        uint256[] memory opens,
        uint256[] memory highs,
        uint256[] memory lows,
        uint256[] memory closes,
        uint256[] memory volumes
    ) {
        PriceCandle[] storage candles = candlesByTimeframe[timeframe];
        uint256 len = candles.length;
        timestamps = new uint256[](len);
        opens = new uint256[](len);
        highs = new uint256[](len);
        lows = new uint256[](len);
        closes = new uint256[](len);
        volumes = new uint256[](len);
        for (uint i = 0; i < len; i++) {
            PriceCandle storage c = candles[i];
            timestamps[i] = c.timestamp;
            opens[i] = c.open;
            highs[i] = c.high;
            lows[i] = c.low;
            closes[i] = c.close;
            volumes[i] = c.volume;
        }
        // Note: DebugLog can't be emitted in view functions, so log during testing via transactions
        return (timestamps, opens, highs, lows, closes, volumes);
    }
    
    function updateAllCandleHistories(uint256 priceInUsd, uint256 amount) internal {
        require(priceInUsd > 0, "Invalid priceInUsd");
        require(amount > 0, "Invalid amount");
        emit DebugLog("updateAllCandleHistories inputs", priceInUsd, amount, block.timestamp);
        
        uint256[6] memory timeframes = [TF_1MIN, TF_5MIN, TF_15MIN, TF_1H, TF_4H, TF_1DAY];
        for (uint256 i = 0; i < timeframes.length; i++) {
            uint256 timeframe = timeframes[i];
            uint256 roundedTimestamp = block.timestamp - (block.timestamp % timeframe);
            PriceCandle[] storage candles = candlesByTimeframe[timeframe];
            bool isNewCandle = candles.length == 0 || candles[candles.length - 1].timestamp < roundedTimestamp;

            uint256 newOpen;
            if (isNewCandle) {
                // Use the last known price or basePrice if no previous candle
                newOpen = candles.length > 0 ? candles[candles.length - 1].close : ((basePrice * getEthUsdPrice()) / 10**18);
                candles.push(PriceCandle({
                    timestamp: roundedTimestamp,
                    open: newOpen,
                    high: priceInUsd > newOpen ? priceInUsd : newOpen,
                    low: priceInUsd < newOpen ? priceInUsd : newOpen,
                    close: priceInUsd,
                    volume: amount
                }));
                emit DebugLog("New candle created", timeframe, roundedTimestamp, amount);
                emit DebugLog("New candle volume", timeframe, roundedTimestamp, amount);
            } else {
                PriceCandle storage lastCandle = candles[candles.length - 1];
                lastCandle.high = priceInUsd > lastCandle.high ? priceInUsd : lastCandle.high;
                lastCandle.low = priceInUsd < lastCandle.low ? priceInUsd : lastCandle.low;
                lastCandle.close = priceInUsd;
                lastCandle.volume += amount;
                emit DebugLog("Existing candle updated", timeframe, roundedTimestamp, lastCandle.volume);
                emit DebugLog("Existing candle volume", timeframe, roundedTimestamp, lastCandle.volume);
            }

            uint256 maxHistory = getMaxCandlesForTimeframe(timeframe);
            while (candles.length > maxHistory) {
                for (uint256 j = 1; j < candles.length; j++) {
                    candles[j - 1] = candles[j];
                }
                candles.pop();
            }

            PriceCandle storage emitCandle = candles[candles.length - 1];
            emit CandleUpdated(
                timeframe,
                roundedTimestamp,
                emitCandle.open,
                emitCandle.high,
                emitCandle.low,
                emitCandle.close,
                emitCandle.volume
            );
            emit DebugLog("Candle data", timeframe, roundedTimestamp, emitCandle.close);
        }
    }

    function mintForTrading() public onlyAuthorized {
        uint256 threshold = userTradingSupply / 10;
        require(balanceOf(address(this)) < threshold, "Supply not low enough to mint");
        uint256 mintAmount = 100_000_000;
        userTradingSupply += mintAmount;
        _mint(address(this), mintAmount);
        emit DebugLog("Minted for trading", mintAmount, userTradingSupply, block.timestamp);
    }

    function getMaxCandlesForTimeframe(uint256 timeframe) internal pure returns (uint256) {
        if (timeframe == TF_1MIN) return MAX_CANDLES_1M;
        if (timeframe == TF_5MIN) return MAX_CANDLES_5M;
        if (timeframe == TF_15MIN) return MAX_CANDLES_15M;
        if (timeframe == TF_1H) return MAX_CANDLES_1H;
        if (timeframe == TF_4H) return MAX_CANDLES_4H;
        if (timeframe == TF_1DAY) return MAX_CANDLES_1D;
        return 500;
    }
}