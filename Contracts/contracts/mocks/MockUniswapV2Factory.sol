// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

interface IUniswapV2Factory {
    function createPair(address tokenA, address tokenB) external returns (address pair);
}

contract MockUniswapV2Factory is IUniswapV2Factory {
    address public mockPair;

    constructor() {
        mockPair = address(this); 
    }

    function createPair(address tokenA, address tokenB) external view override returns (address pair) {
        tokenA; tokenB;
        return mockPair; 
    }
}