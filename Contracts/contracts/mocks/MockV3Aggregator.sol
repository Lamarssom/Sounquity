// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@chainlink/contracts/src/v0.8/shared/interfaces/AggregatorV3Interface.sol";

contract MockV3Aggregator is AggregatorV3Interface {
    uint8 public override decimals;
    int256 public price;
    string public override description;
    uint256 public override version;

    constructor(uint8 _decimals, int256 _initialPrice) {
        decimals = _decimals;
        price = _initialPrice;
        description = "Mock ETH/USD Aggregator";
        version = 3;
    }

    function latestRoundData()
        external
        view
        override
        returns (
            uint80 roundId,
            int256 answer,
            uint256 startedAt,
            uint256 updatedAt,
            uint80 answeredInRound
        )
    {
        return (1, price, block.timestamp, block.timestamp, 1);
    }

    function getRoundData(uint80 _roundId)
        external
        view
        override
        returns (
            uint80 roundId,
            int256 answer,
            uint256 startedAt,
            uint256 updatedAt,
            uint80 answeredInRound
        )
    {
        return (_roundId, price, block.timestamp, block.timestamp, _roundId);
    }

    function setPrice(int256 _price) external {
        price = _price;
    }
}