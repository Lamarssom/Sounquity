// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@uniswap/v2-periphery/contracts/interfaces/IUniswapV2Router02.sol";
import "@uniswap/v2-core/contracts/interfaces/IUniswapV2Factory.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

contract ArtistSharesToken is ERC20, Ownable {
    AggregatorV3Interface public priceFeed;
    IUniswapV2Router02 public uniswapRouter;
    address public uniswapPair;
    address public platformWallet;

    // Supply and allocations
    uint256 public constant TOTAL_SUPPLY = 1_000_000_000 * 10**18; // 1B tokens
    uint256 public constant CURVE_SUPPLY = TOTAL_SUPPLY * 80 / 100; // 800M for curve
    uint256 public constant TEAM_SUPPLY = TOTAL_SUPPLY * 10 / 100;  // 100M vested
    uint256 public constant AIRDROP_SUPPLY = TOTAL_SUPPLY * 5 / 100; // 50M vested
    uint256 public constant PLATFORM_SUPPLY = TOTAL_SUPPLY * 5 / 100; // 50M platform

    // Vesting (3 years, monthly)
    uint256 public constant VESTING_DURATION = 3 * 365 * 24 * 60 * 60; // 3 years in seconds
    uint256 public constant VESTING_PERIODS = 36; // Monthly
    uint256 public vestingStart;
    mapping(address => uint256) public teamVested;
    mapping(address => uint256) public airdropVested;
    uint256 public totalTeamVested;
    uint256 public totalAirdropVested;

    // Fees
    uint256 public constant BUY_FEE = 50;  // 0.5% (basis points)
    uint256 public constant SELL_FEE_BASE = 100; // 1% base
    uint256 public constant MAX_SELL_FEE = 600; // 1% + up to 5% progressive

    // Bonding curve
    uint256 public constant INITIAL_PRICE = 1; // 0.000000000000000001 ETH
    uint256 public constant BASE_CURVE_CONSTANT = 1; // k, scaled by 1e18 in calcs
    uint256 public constant CURVE_SCALE = 1e18; // Denominator for k (simulates 1e-12)
    uint256 public tokensInCurve;
    uint256 public ethInCurve;
    bool public curveComplete;
    uint256 public completionThreshold = 1_000_000 * 10**8; // $1M MC in USD
    uint256 public popularity; // 0-100, scales curve

    // Limits and anti-dump
    uint256 public constant DAILY_SELL_LIMIT = TOTAL_SUPPLY / 20; // 5% supply
    uint256 public constant SELL_COOLDOWN = 1 hours;
    mapping(address => uint256) public dailySellVolume;
    mapping(address => uint256) public lastSellTime;
    uint256 public lastResetDay;

    // Candle timeframes
    uint256 public constant TF_1MIN = 60;
    uint256 public constant TF_5MIN = 300;
    uint256 public constant TF_15MIN = 900;
    uint256 public constant TF_30MIN = 1800;
    uint256 public constant TF_1H = 3600;
    uint256 public constant TF_4H = 14400;
    uint256 public constant TF_1DAY = 86400;
    uint256 public constant TF_1WEEK = 604800;
    uint256 public constant MAX_CANDLES_1M = 1000;
    uint256 public constant MAX_CANDLES_5M = 850;
    uint256 public constant MAX_CANDLES_15M = 700;
    uint256 public constant MAX_CANDLES_30M = 600;
    uint256 public constant MAX_CANDLES_1H = 500;
    uint256 public constant MAX_CANDLES_4H = 400;
    uint256 public constant MAX_CANDLES_1D = 365;
    uint256 public constant MAX_CANDLES_1W = 52;

    // Candle struct
    struct PriceCandle {
        uint256 timestamp;
        uint256 open;
        uint256 high;
        uint256 low;
        uint256 close;
        uint256 volume;
    }
    mapping(uint256 => PriceCandle[]) public candlesByTimeframe;
    uint256 public totalVolumeTraded;

    // Events
    event SharesBought(address indexed buyer, uint256 amount, uint256 price, uint256 timestamp);
    event SharesSold(address indexed seller, uint256 amount, uint256 price, uint256 timestamp);
    event CurveCompleted(uint256 ethLiquidity, uint256 tokenLiquidity);
    event CandleUpdated(uint256 timeframe, uint256 timestamp, uint256 open, uint256 high, uint256 low, uint256 close, uint256 volume);
    event DebugLog(string message, uint256 value1, uint256 value2, uint256 value3);
    event AirdropDistributed(address indexed user, uint256 amount);

    constructor(
        string memory name,
        string memory symbol,
        address _teamWallet,
        address _platformWallet,
        address _priceFeed,
        address _uniswapRouter,
        uint256 _popularity
    ) ERC20(name, symbol) Ownable(msg.sender) {
        require(_popularity <= 100, "Popularity must be 0-100");
        priceFeed = AggregatorV3Interface(_priceFeed);
        uniswapRouter = IUniswapV2Router02(_uniswapRouter);
        platformWallet = _platformWallet;
        popularity = _popularity;

        // Mint allocations
        _mint(address(this), CURVE_SUPPLY);
        tokensInCurve = CURVE_SUPPLY;
        _mint(address(this), TEAM_SUPPLY);
        _mint(address(this), AIRDROP_SUPPLY);
        _mint(_platformWallet, PLATFORM_SUPPLY);

        // Setup vesting (20% team immediate, 80% vested; airdrop fully vested)
        vestingStart = block.timestamp;
        teamVested[_teamWallet] = TEAM_SUPPLY;
        totalTeamVested = TEAM_SUPPLY;
        totalAirdropVested = AIRDROP_SUPPLY;

        // Uniswap pair
        address factory = uniswapRouter.factory();
        uniswapPair = IUniswapV2Factory(factory).createPair(address(this), uniswapRouter.WETH());

        ethInCurve = 1 wei; // Avoid div0
        lastResetDay = block.timestamp / 1 days;

        // Initialize candles
        uint256[8] memory timeframes = [TF_1MIN, TF_5MIN, TF_15MIN, TF_30MIN, TF_1H, TF_4H, TF_1DAY, TF_1WEEK];
        uint256 initialPriceInUsd = (INITIAL_PRICE * getEthUsdPrice()) / 10**18;
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
            emit CandleUpdated(timeframe, roundedTimestamp, initialPriceInUsd, initialPriceInUsd, initialPriceInUsd, initialPriceInUsd, 0);
            emit DebugLog("Initial candle created", timeframe, roundedTimestamp, initialPriceInUsd);
        }
    }

    function getEthUsdPrice() public view returns (uint256) {
        (, int256 price,,,) = priceFeed.latestRoundData();
        require(price > 0, "Invalid Chainlink price");
        return uint256(price);
    }

    function getCurrentPrice() public view returns (uint256) {
        if (curveComplete) return 0; // Post-migration, use Uniswap
        uint256 k = (BASE_CURVE_CONSTANT * popularity) / 100; // Popularity multiplier
        uint256 supplySold = TOTAL_SUPPLY - tokensInCurve;
        return (k * supplySold * supplySold) / CURVE_SCALE; // Quadratic: k * supply_sold^2 / 1e18
    }

    function buy(uint256 minTokensOut) external payable {
        require(!curveComplete, "Curve complete");
        require(msg.value > 0, "No ETH sent");

        uint256 fee = (msg.value * BUY_FEE) / 10000;
        uint256 ethIn = msg.value - fee;
        uint256 tokensOut = getTokensForEth(ethIn);

        require(tokensOut >= minTokensOut, "Slippage too high");
        require(tokensOut <= tokensInCurve, "Insufficient tokens");

        ethInCurve = ethInCurve + ethIn;
        tokensInCurve = tokensInCurve - tokensOut;

        payable(platformWallet).transfer(fee);
        _transfer(address(this), msg.sender, tokensOut);

        totalVolumeTraded = totalVolumeTraded + tokensOut;
        updateAllCandleHistories((getCurrentPrice() * getEthUsdPrice()) / 10**18, tokensOut);

        _checkCurveCompletion();

        emit SharesBought(msg.sender, tokensOut, getCurrentPrice(), block.timestamp);
    }

    function sell(uint256 tokensIn, uint256 minEthOut) external {
        require(!curveComplete, "Curve complete");
        require(tokensIn > 0, "Invalid amount");
        resetDailySellIfNewDay(msg.sender);

        uint256 priceBefore = getCurrentPrice();
        uint256 ethOut = getEthForTokens(tokensIn);
        uint256 priceAfter = getCurrentPriceAfterSell(tokensIn);
        require(priceBefore <= (priceAfter * 105) / 100, "Sell exceeds 5% price impact"); // Cap price drop
        uint256 sellLimit = DAILY_SELL_LIMIT;
        uint256 sellLimitUsd = (sellLimit * priceBefore * getEthUsdPrice()) / (10**18 * 10**8);
        require(sellLimitUsd <= (50_000 * 10**8), "Sell limit exceeds USD cap"); // ~$50K max
        require(dailySellVolume[msg.sender] + tokensIn <= sellLimit, "Exceeds daily sell limit");
        require(block.timestamp >= lastSellTime[msg.sender] + SELL_COOLDOWN, "Cooldown active");

        uint256 fee = (ethOut * calculateSellFee(tokensIn)) / 10000;
        uint256 ethAfterFee = ethOut - fee;

        require(ethAfterFee >= minEthOut, "Slippage too high");
        require(ethAfterFee <= ethInCurve, "Insufficient ETH");

        _transfer(msg.sender, address(this), tokensIn);

        ethInCurve = ethInCurve - ethAfterFee;
        tokensInCurve = tokensInCurve + tokensIn;

        uint256 burnAmount = fee / 2; // 50% burn
        payable(platformWallet).transfer(fee - burnAmount);
        payable(address(0xdead)).transfer(burnAmount); // Burn

        payable(msg.sender).transfer(ethAfterFee);

        dailySellVolume[msg.sender] = dailySellVolume[msg.sender] + tokensIn;
        lastSellTime[msg.sender] = block.timestamp;
        totalVolumeTraded = totalVolumeTraded + tokensIn;

        updateAllCandleHistories((getCurrentPrice() * getEthUsdPrice()) / 10**18, tokensIn);

        _checkCurveCompletion();

        emit SharesSold(msg.sender, tokensIn, getCurrentPrice(), block.timestamp);
    }

    function calculateSellFee(uint256 tokensIn) public view returns (uint256) {
        uint256 userBalance = balanceOf(msg.sender);
        uint256 percent = (tokensIn * 100) / userBalance;
        uint256 extraFee = 0;
        if (percent > 5) extraFee = (percent - 5) * 100 * 2; // +0.5% per 1% over 0.5%
        uint256 totalFee = SELL_FEE_BASE + extraFee;
        return totalFee > MAX_SELL_FEE ? MAX_SELL_FEE : totalFee;
    }

    function getTokensForEth(uint256 ethAmount) public view returns (uint256) {
        if (curveComplete) return 0;
        uint256 k = (BASE_CURVE_CONSTANT * popularity) / 100;
        uint256 supplySold = TOTAL_SUPPLY - tokensInCurve;
        uint256 newSupplySold = sqrt((ethAmount * CURVE_SCALE) / k + supplySold * supplySold);
        return newSupplySold - supplySold;
    }

    function getEthForTokens(uint256 tokenAmount) public view returns (uint256) {
        if (curveComplete) return 0;
        uint256 k = (BASE_CURVE_CONSTANT * popularity) / 100;
        uint256 supplySold = TOTAL_SUPPLY - tokensInCurve;
        uint256 newSupplySold = supplySold - tokenAmount;
        return (k * supplySold * supplySold - k * newSupplySold * newSupplySold) / CURVE_SCALE;
    }

    function getCurrentPriceAfterSell(uint256 tokenAmount) public view returns (uint256) {
        if (curveComplete) return 0;
        uint256 k = (BASE_CURVE_CONSTANT * popularity) / 100;
        uint256 supplySold = TOTAL_SUPPLY - tokensInCurve - tokenAmount;
        return (k * supplySold * supplySold) / CURVE_SCALE;
    }

    function _checkCurveCompletion() internal {
        if (curveComplete) return;
        uint256 mcUsd = (getCurrentPrice() * TOTAL_SUPPLY / 10**18) * getEthUsdPrice() / 10**8;
        if (mcUsd >= completionThreshold) {
            curveComplete = true;
            uint256 tokenLiq = tokensInCurve;
            uint256 ethLiq = ethInCurve / 2; // 50% ETH to LP
            _approve(address(this), address(uniswapRouter), tokenLiq);
            uniswapRouter.addLiquidityETH{value: ethLiq}(
                address(this),
                tokenLiq,
                0,
                0,
                owner(),
                block.timestamp
            );
            ethInCurve = ethInCurve - ethLiq; // Keep rest for platform
            emit CurveCompleted(ethLiq, tokenLiq);
        }
    }

    function distributeAirdrop(address user, uint256 amount) external onlyOwner {
        amount = amount * 10**18;
        uint256 unlocked = getUnlockedAirdrop();
        require(amount <= unlocked, "Not enough unlocked airdrop tokens");
        require(!curveComplete, "Curve complete");
        _transfer(address(this), user, amount);
        totalAirdropVested = totalAirdropVested - amount;
        emit AirdropDistributed(user, amount);
    }

    function getUnlockedTeam(address team) public view returns (uint256) {
        if (block.timestamp < vestingStart) return (teamVested[team] * 20) / 100; // 20% immediate
        uint256 elapsed = block.timestamp - vestingStart;
        uint256 vestedAmount = (teamVested[team] * 80) / 100; // 80% vested part
        uint256 vested = (vestedAmount * elapsed) / VESTING_DURATION;
        uint256 immediate = (teamVested[team] * 20) / 100;
        return immediate + (vested > vestedAmount ? vestedAmount : vested);
    }

    function getUnlockedAirdrop() public view returns (uint256) {
        if (block.timestamp < vestingStart) return 0;
        uint256 elapsed = block.timestamp - vestingStart;
        uint256 vested = (totalAirdropVested * elapsed) / VESTING_DURATION;
        return vested > totalAirdropVested ? totalAirdropVested : vested;
    }

    function claimTeamVested() external {
        uint256 unlocked = getUnlockedTeam(msg.sender);
        require(unlocked > 0, "No vested tokens");
        teamVested[msg.sender] = teamVested[msg.sender] - unlocked;
        totalTeamVested = totalTeamVested - unlocked;
        _transfer(address(this), msg.sender, unlocked);
    }

    function updateAllCandleHistories(uint256 priceInUsd, uint256 amount) internal {
        uint256[8] memory timeframes = [TF_1MIN, TF_5MIN, TF_15MIN, TF_30MIN, TF_1H, TF_4H, TF_1DAY, TF_1WEEK];
        for (uint256 i = 0; i < timeframes.length; i++) {
            uint256 timeframe = timeframes[i];
            uint256 roundedTimestamp = block.timestamp - (block.timestamp % timeframe);
            PriceCandle[] storage candles = candlesByTimeframe[timeframe];
            bool isNewCandle = candles.length == 0 || candles[candles.length - 1].timestamp < roundedTimestamp;

            if (isNewCandle) {
                uint256 newOpen = candles.length > 0 ? candles[candles.length - 1].close : priceInUsd;
                candles.push(PriceCandle({
                    timestamp: roundedTimestamp,
                    open: priceInUsd,
                    high: priceInUsd,
                    low: priceInUsd,
                    close: priceInUsd,
                    volume: amount
                }));
                emit DebugLog("New candle created", timeframe, roundedTimestamp, amount);
            } else {
                PriceCandle storage lastCandle = candles[candles.length - 1];
                lastCandle.high = priceInUsd > lastCandle.high ? priceInUsd : lastCandle.high;
                lastCandle.low = priceInUsd < lastCandle.low ? priceInUsd : lastCandle.low;
                lastCandle.close = priceInUsd;
                lastCandle.volume = lastCandle.volume + amount;
                emit DebugLog("Existing candle updated", timeframe, roundedTimestamp, lastCandle.volume);
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
        }
    }

    function resetDailySellIfNewDay(address user) internal {
        uint256 currentDay = block.timestamp / 1 days;
        if (currentDay > lastResetDay) {
            dailySellVolume[user] = 0;
            lastResetDay = currentDay;
        }
    }

    function getMaxCandlesForTimeframe(uint256 timeframe) internal pure returns (uint256) {
        if (timeframe == TF_1MIN) return MAX_CANDLES_1M;
        if (timeframe == TF_5MIN) return MAX_CANDLES_5M;
        if (timeframe == TF_15MIN) return MAX_CANDLES_15M;
        if (timeframe == TF_30MIN) return MAX_CANDLES_30M;
        if (timeframe == TF_1H) return MAX_CANDLES_1H;
        if (timeframe == TF_4H) return MAX_CANDLES_4H;
        if (timeframe == TF_1DAY) return MAX_CANDLES_1D;
        if (timeframe == TF_1WEEK) return MAX_CANDLES_1W;
        return 500;
    }

    function sqrt(uint256 y) internal pure returns (uint256 z) {
        if (y > 3) {
            z = y;
            uint256 x = y / 2 + 1;
            while (x < z) {
                z = x;
                x = (y / x + x) / 2;
            }
        } else if (y != 0) {
            z = 1;
        }
        return z;
    }

    function setDailySellLimit(uint256 newLimitUsd) external onlyOwner {
        require(newLimitUsd >= 10_000 * 10**8, "Limit too low");
        // Convert USD to tokens dynamically in factory or admin logic
    }

    receive() external payable {}
}
