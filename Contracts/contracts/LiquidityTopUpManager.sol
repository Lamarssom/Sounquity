// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract LiquidityTopUpManager {

    address public owner;
    uint256 public minimumBalance;  // Minimum ETH balance threshold for artist contract
    uint256 public maxTopUpAmount;  // Max amount allowed per top-up to avoid accidental over-funding

    event TopUpPerformed(address indexed artistContract, uint256 amount);
    event OwnerChanged(address indexed oldOwner, address indexed newOwner);
    event ThresholdsUpdated(uint256 newMinimumBalance, uint256 newMaxTopUpAmount);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not authorized");
        _;
    }

    constructor(uint256 _minimumBalance, uint256 _maxTopUpAmount) {
        owner = msg.sender;
        minimumBalance = _minimumBalance;
        maxTopUpAmount = _maxTopUpAmount;
    }

    function topUp(address payable artistContract, uint256 amount) external onlyOwner {
        require(amount <= maxTopUpAmount, "Exceeds max top-up limit");
        require(address(this).balance >= amount, "Insufficient liquidity manager balance");
        require(artistContract.balance < minimumBalance, "Artist contract still has sufficient balance");

        artistContract.transfer(amount);
        emit TopUpPerformed(artistContract, amount);
    }

    function updateThresholds(uint256 _minimumBalance, uint256 _maxTopUpAmount) external onlyOwner {
        minimumBalance = _minimumBalance;
        maxTopUpAmount = _maxTopUpAmount;
        emit ThresholdsUpdated(_minimumBalance, _maxTopUpAmount);
    }

    function changeOwner(address newOwner) external onlyOwner {
        emit OwnerChanged(owner, newOwner);
        owner = newOwner;
    }

    function deposit() external payable onlyOwner {}

    function withdraw(uint256 amount) external onlyOwner {
        require(address(this).balance >= amount, "Insufficient balance");
        payable(owner).transfer(amount);
    }

    function getLiquidityManagerBalance() external view returns (uint256) {
        return address(this).balance;
    }
}