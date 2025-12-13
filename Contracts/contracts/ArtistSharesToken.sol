// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@uniswap/v2-periphery/contracts/interfaces/IUniswapV2Router02.sol";
import "@uniswap/v2-core/contracts/interfaces/IUniswapV2Factory.sol";
import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

contract ArtistSharesToken is ERC20, Ownable {
    uint256 public constant TOTAL_SUPPLY      = 1_000_000_000 * 10**18;
    uint256 public constant CURVE_TOKENS      = TOTAL_SUPPLY * 80 / 100;  
    uint256 public constant TEAM_SUPPLY       = TOTAL_SUPPLY * 10 / 100;
    uint256 public constant AIRDROP_SUPPLY    = TOTAL_SUPPLY * 5 / 100;
    uint256 public constant PLATFORM_SUPPLY   = TOTAL_SUPPLY * 5 / 100;

    uint256 public constant VESTING_DURATION  = 3 * 365 * 24 * 60 * 60;
    uint256 public constant VESTING_PERIODS   = 36;

    uint256 public constant BUY_FEE           = 50;     
    uint256 public constant SELL_FEE_BASE     = 100;     
    uint256 public constant MAX_SELL_FEE      = 600;     

    uint256 public constant USD_FRACTIONAL_DECIMALS = 8;

    uint256 public constant TARGET_FDV_USD    = 69_000 * 10**8;
    uint256 public constant SLOPE_NUMERATOR   = 1_000_000_000_000;

    uint256 public constant MIN_TOKENS_FOR_LP = 100_000_000 * 10**18;

    uint256 public constant DAILY_SELL_LIMIT  = TOTAL_SUPPLY / 20;
    uint256 public dailySellLimitUsd          = 50_000 * 10**8;
    uint256 public constant SELL_COOLDOWN     = 1 hours;

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

    AggregatorV3Interface public priceFeed;
    IUniswapV2Router02 public uniswapRouter;
    address public uniswapPair;
    address public platformWallet;

    uint256 public vestingStart;
    mapping(address => uint256) public teamVested;
    mapping(address => uint256) public airdropVested;
    uint256 public totalTeamVested;
    uint256 public totalAirdropVested;

    uint256 public tokensSold;
    uint256 public ethInCurve;
    bool public curveComplete;

    mapping(address => uint256) public dailySellVolume;
    mapping(address => uint256) public lastSellTime;
    uint256 public lastResetDay;

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

    event SharesBought(address indexed buyer, uint256 amount, uint256 priceMicroCents, uint256 timestamp,  uint256 ethSpent);
    event SharesSold(address indexed seller, uint256 amount, uint256 priceMicroCents, uint256 timestamp, uint256 ethReceived);
    event CurveCompleted(uint256 ethLiquidity, uint256 tokenLiquidity);
    event CandleUpdated(uint256 timeframe, uint256 timestamp, uint256 open, uint256 high, uint256 low, uint256 close, uint256 volume);
    event AirdropDistributed(address indexed user, uint256 amount);
    event DailySellLimitUpdated(uint256 newLimitUsd, uint256 timestamp);

    constructor(
        string memory name,
        string memory symbol,
        address _teamWallet,
        address _platformWallet,
        address _priceFeed,
        address _uniswapRouter
    ) ERC20(name, symbol) Ownable(msg.sender) {
        priceFeed      = AggregatorV3Interface(_priceFeed);
        uniswapRouter  = IUniswapV2Router02(_uniswapRouter);
        platformWallet = _platformWallet;

        _mint(address(this), CURVE_TOKENS);
        _mint(address(this), TEAM_SUPPLY);
        _mint(address(this), AIRDROP_SUPPLY);
        _mint(_platformWallet, PLATFORM_SUPPLY);

        vestingStart = block.timestamp;
        teamVested[_teamWallet] = TEAM_SUPPLY;
        totalTeamVested = TEAM_SUPPLY;
        totalAirdropVested = AIRDROP_SUPPLY;

        if (_uniswapRouter != address(0)) {
            address factory = uniswapRouter.factory();
            uniswapPair = IUniswapV2Factory(factory).createPair(address(this), uniswapRouter.WETH());
        }

        ethInCurve = 0;                
        tokensSold = 0;
        lastResetDay = block.timestamp / 1 days;

        uint256[8] memory tfs = [TF_1MIN, TF_5MIN, TF_15MIN, TF_30MIN, TF_1H, TF_4H, TF_1DAY, TF_1WEEK];
        for (uint256 i = 0; i < tfs.length; i++) {
            uint256 tf = tfs[i];
            uint256 ts = block.timestamp - (block.timestamp % tf);
            candlesByTimeframe[tf].push(PriceCandle(ts, 0, 0, 0, 0, 0));
            emit CandleUpdated(tf, ts, 0, 0, 0, 0, 0);
        }
    }


    function getEthUsdPrice() public view returns (uint256) {
        (, int256 price,,,) = priceFeed.latestRoundData();
        if (price <= 0) {
            return 3500 * 10**8;
        }
        return uint256(price);
    }

    function _microCentsToUsd(uint256 microCents) internal pure returns (uint256) {
        return microCents / (100 * 10**USD_FRACTIONAL_DECIMALS);
    }

    function getPriceUsd() public view returns (uint256) {
        return getCurrentPriceMicroUSD() / 1e8;
    }

    function _sqrt(uint256 x) internal pure returns (uint256) {
        if (x == 0) return 0;
        uint256 z = (x + 1) / 2;
        uint256 y = x;
        while (z < y) {
            y = z;
            z = (x / z + z) / 2;
        }
        return y;
    }

    function getCurrentPriceMicroUSD() public view returns (uint256) {
        return _getVirtualPriceMicroUSD(); 
    }

    function getEthNeededForBuy(uint256 tokenAmount) public view returns (uint256) {
        if (tokenAmount == 0 || curveComplete) return 0;

        uint256 maxTokens = CURVE_TOKENS - tokensSold;
        if (tokenAmount > maxTokens) tokenAmount = maxTokens;

        uint256 ethCurrent = ethInCurve;
        uint256 tokensWanted = tokenAmount;

        uint256 ethInNormal;
        if (ethCurrent == 0) {
            ethInNormal = (tokensWanted * tokensWanted) / (2 * SLOPE_NUMERATOR);
            if (ethInNormal == 0) ethInNormal = tokensWanted / 1e18;
        } else {
            uint256 denom = 2 * ethCurrent;
            if (denom == 0) return 0; 
            uint256 numerator = tokensWanted * tokensWanted + 2 * ethCurrent * tokensWanted;
            ethInNormal = numerator / denom;
        }

        uint256 ethWithFee = (ethInNormal * 10_000) / (10_000 - BUY_FEE);
        return ethWithFee;
    }

    function tokensInCurve() public view returns (uint256) {
        return CURVE_TOKENS - tokensSold;
    }

    function _getVirtualPriceMicroUSD() internal view returns (uint256) {
        uint256 ethUsd = getEthUsdPrice();

        uint256 virtualEth  = ethInCurve + 50_000_000_000_000;         
        uint256 virtualSold = tokensSold + 100_000_000_000_000_000_000;   

        uint256 priceMicro = (SLOPE_NUMERATOR * virtualEth * ethUsd) 
                            / (virtualSold * 10_000_000_000_000_000); 

        return priceMicro;
    }

    
    function buy(uint256 minTokensOut) external payable {
        require(!curveComplete, "Curve complete");
        require(msg.value > 0, "No ETH");

        uint256 fee = (msg.value * BUY_FEE) / 10_000;
        uint256 ethIn = msg.value - fee;

        uint256 tokensOut = getTokensForEth(ethIn);
        require(tokensOut >= minTokensOut, "Slippage");
        require(tokensSold + tokensOut <= CURVE_TOKENS, "Exceeds curve");

        ethInCurve += ethIn;
        tokensSold += tokensOut;

        payable(platformWallet).transfer(fee);
        _transfer(address(this), msg.sender, tokensOut);

        totalVolumeTraded += tokensOut;
        updateAllCandleHistories(_microCentsToUsd(getCurrentPriceMicroUSD()), tokensOut);

        _checkCurveCompletion();

        emit SharesBought(msg.sender, tokensOut, _getVirtualPriceMicroUSD(), block.timestamp, msg.value);
    }

    function sell(uint256 tokensIn, uint256 minEthOut) external {
        require(!curveComplete, "Curve complete");
        require(tokensIn > 0, "Zero amount");
        resetDailySellIfNewDay(msg.sender);

        uint256 priceBefore = getCurrentPriceMicroUSD();
        uint256 ethOut = getEthForTokens(tokensIn);
        require(priceBefore >= (getCurrentPriceAfterSell(tokensIn) * 95) / 100, ">5% impact");

    
        uint256 sellValueUsdMicro = (tokensIn * priceBefore) / 1e18;
        require(sellValueUsdMicro <= dailySellLimitUsd, "Exceeds daily USD limit");

        require(dailySellVolume[msg.sender] + tokensIn <= DAILY_SELL_LIMIT, "Daily token cap");
        require(block.timestamp >= lastSellTime[msg.sender] + SELL_COOLDOWN, "Cooldown");

        uint256 feeBps = calculateSellFee(tokensIn);
        uint256 fee = (ethOut * feeBps) / 10_000;
        uint256 ethAfterFee = ethOut - fee;

        require(ethAfterFee >= minEthOut, "Slippage");
        require(ethAfterFee <= ethInCurve, "Not enough ETH in curve");

        _transfer(msg.sender, address(this), tokensIn);

        ethInCurve -= ethAfterFee;
        tokensSold -= tokensIn;

        uint256 burn = fee / 2;
        payable(platformWallet).transfer(fee - burn);
        payable(address(0xdead)).transfer(burn);

        payable(msg.sender).transfer(ethAfterFee);

        dailySellVolume[msg.sender] += tokensIn;
        lastSellTime[msg.sender] = block.timestamp;
        totalVolumeTraded += tokensIn;

        updateAllCandleHistories(_microCentsToUsd(getCurrentPriceMicroUSD()), tokensIn);

        _checkCurveCompletion();

        emit SharesSold(msg.sender, tokensIn, _getVirtualPriceMicroUSD(), block.timestamp, ethAfterFee);
    }

    function getTokensForEth(uint256 ethIn) public view returns (uint256) {
        if (ethIn == 0 || curveComplete) return 0;

        uint256 maxTokens = CURVE_TOKENS - tokensSold;
        if (maxTokens == 0) return 0;

        uint256 a = ethInCurve;
        uint256 b = ethIn;

        uint256 tokensOut;
        if (a == 0) {
            uint256 discriminant = 2 * SLOPE_NUMERATOR * b * 1e18;
            uint256 sqrtDisc = _sqrt(discriminant);
            tokensOut = sqrtDisc;
            if (tokensOut == 0) tokensOut = b * 1e18 / 1;
        } else {
            uint256 discriminant = a * a + 2 * a * b * SLOPE_NUMERATOR;
            uint256 sqrtDisc = _sqrt(discriminant);
            tokensOut = sqrtDisc - a;
            if (tokensOut == 0) tokensOut = b * 1e18 / ((SLOPE_NUMERATOR * a) / tokensSold + 1);
        }

        if (tokensOut > maxTokens) tokensOut = maxTokens;
        return tokensOut;
    }

    function getEthForTokens(uint256 tokenAmount) public view returns (uint256) {
        if (tokenAmount == 0 || curveComplete || tokenAmount > tokensSold) return 0;

        uint256 newSold = tokensSold - tokenAmount;
        if (newSold == 0) return ethInCurve;

        return (tokenAmount * ethInCurve) / tokensSold;
    }

    function getCurrentPriceAfterSell(uint256 tokenAmount) public view returns (uint256) {
        if (curveComplete) return 0;
        uint256 newSold = tokensSold > tokenAmount ? tokensSold - tokenAmount : 0;
        if (newSold == 0) return 0;

        uint256 priceWeiPerToken = (SLOPE_NUMERATOR * ethInCurve) / newSold;
        uint256 ethUsd = getEthUsdPrice();
        return (priceWeiPerToken * ethUsd) / 1e18;
    }

    function _checkCurveCompletion() internal {
        if (curveComplete) return;

        uint256 fdvMicro = getCurrentPriceMicroUSD() * (TOTAL_SUPPLY / 10**18);
        if (fdvMicro >= TARGET_FDV_USD && tokensSold >= MIN_TOKENS_FOR_LP) {
            curveComplete = true;

            uint256 tokenLiq = CURVE_TOKENS - tokensSold;
            uint256 ethLiq = ethInCurve / 2;

            _approve(address(this), address(uniswapRouter), tokenLiq);
            uniswapRouter.addLiquidityETH{value: ethLiq}(
                address(this), tokenLiq, 0, 0, owner(), block.timestamp + 300
            );

            ethInCurve -= ethLiq;
            emit CurveCompleted(ethLiq, tokenLiq);
        }
    }

    function calculateSellFee(uint256 tokensIn) public view returns (uint256) {
        uint256 bal = balanceOf(msg.sender);
        if (bal == 0) return SELL_FEE_BASE;
        uint256 pct = (tokensIn * 100) / bal;
        uint256 extra = pct > 5 ? (pct - 5) * 100 * 2 : 0;
        uint256 total = SELL_FEE_BASE + extra;
        return total > MAX_SELL_FEE ? MAX_SELL_FEE : total;
    }

    function distributeAirdrop(address user, uint256 amount) external onlyOwner {
        amount = amount * 10**18;
        uint256 unlocked = getUnlockedAirdrop();
        require(amount <= unlocked, "Not enough airdrop");
        require(!curveComplete, "Curve complete");
        _transfer(address(this), user, amount);
        totalAirdropVested -= amount;
        emit AirdropDistributed(user, amount);
    }

    function getUnlockedTeam(address team) public view returns (uint256) {
        if (block.timestamp < vestingStart) return (teamVested[team] * 20) / 100;
        uint256 elapsed = block.timestamp - vestingStart;
        uint256 vestedPart = (teamVested[team] * 80) / 100;
        uint256 vested = (vestedPart * elapsed) / VESTING_DURATION;
        uint256 immediate = (teamVested[team] * 20) / 100;
        return immediate + (vested > vestedPart ? vestedPart : vested);
    }

    function getUnlockedAirdrop() public view returns (uint256) {
        if (block.timestamp < vestingStart) return 0;
        uint256 elapsed = block.timestamp - vestingStart;
        uint256 vested = (totalAirdropVested * elapsed) / VESTING_DURATION;
        return vested > totalAirdropVested ? totalAirdropVested : vested;
    }

    function claimTeamVested() external {
        uint256 unlocked = getUnlockedTeam(msg.sender);
        require(unlocked > 0, "Nothing vested");
        teamVested[msg.sender] -= unlocked;
        totalTeamVested -= unlocked;
        _transfer(address(this), msg.sender, unlocked);
    }

    function updateAllCandleHistories(uint256 priceUsd, uint256 amount) internal {
        uint256[8] memory tfs = [TF_1MIN, TF_5MIN, TF_15MIN, TF_30MIN, TF_1H, TF_4H, TF_1DAY, TF_1WEEK];
        for (uint256 i = 0; i < tfs.length; i++) {
            uint256 tf = tfs[i];
            uint256 ts = block.timestamp - (block.timestamp % tf);
            PriceCandle[] storage arr = candlesByTimeframe[tf];
            bool newCandle = arr.length == 0 || arr[arr.length-1].timestamp < ts;

            if (newCandle) {
                uint256 open = arr.length > 0 ? arr[arr.length-1].close : priceUsd;
                arr.push(PriceCandle(ts, open, priceUsd, priceUsd, priceUsd, amount / 10**18));
            } else {
                PriceCandle storage c = arr[arr.length-1];
                if (priceUsd > c.high) c.high = priceUsd;
                if (priceUsd < c.low)  c.low  = priceUsd;
                c.close = priceUsd;
                c.volume += amount / 10**18;
            }

            uint256 max = getMaxCandlesForTimeframe(tf);
            while (arr.length > max) {
                for (uint256 j = 1; j < arr.length; j++) arr[j-1] = arr[j];
                arr.pop();
            }

            PriceCandle storage e = arr[arr.length-1];
            emit CandleUpdated(tf, ts, e.open, e.high, e.low, e.close, e.volume);
        }
    }

    function resetDailySellIfNewDay(address user) internal {
        uint256 cur = block.timestamp / 1 days;
        if (cur > lastResetDay) {
            dailySellVolume[user] = 0;
            lastResetDay = cur;
        }
    }

    function getMaxCandlesForTimeframe(uint256 tf) internal pure returns (uint256) {
        if (tf == TF_1MIN) return MAX_CANDLES_1M;
        if (tf == TF_5MIN) return MAX_CANDLES_5M;
        if (tf == TF_15MIN) return MAX_CANDLES_15M;
        if (tf == TF_30MIN) return MAX_CANDLES_30M;
        if (tf == TF_1H) return MAX_CANDLES_1H;
        if (tf == TF_4H) return MAX_CANDLES_4H;
        if (tf == TF_1DAY) return MAX_CANDLES_1D;
        if (tf == TF_1WEEK) return MAX_CANDLES_1W;
        return 500;
    }

    function setDailySellLimit(uint256 newLimitUsd) external onlyOwner {
        require(newLimitUsd >= 10_000 * 10**8, "Too low");
        dailySellLimitUsd = newLimitUsd;
        emit DailySellLimitUpdated(newLimitUsd, block.timestamp);
    }

    receive() external payable {}
}