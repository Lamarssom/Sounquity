// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

interface IUniswapV2Router02 {
    function factory() external view returns (address);
    function WETH() external pure returns (address);
    function addLiquidityETH(
        address token,
        uint amountTokenDesired,
        uint amountTokenMin,
        uint amountETHMin,
        address to,
        uint deadline
    ) external payable returns (uint amountETH, uint amountToken, uint liquidity);
}

contract MockUniswapV2Router is IUniswapV2Router02 {
    address internal _factory; 
    address public constant WETH_ADDRESS = address(0xdead);

    constructor(address factoryAddr) {
        _factory = factoryAddr;
    }

    function factory() external view override returns (address) { 
        return _factory;
    }

    function WETH() external pure override returns (address) {
        return WETH_ADDRESS;
    }

    function addLiquidityETH(
        address,
        uint amountTokenDesired,
        uint, 
        uint, 
        address,
        uint 
    ) external payable override returns (uint amountETH, uint amountToken, uint liquidity) {
        return (msg.value, amountTokenDesired, 1); 
    }
}