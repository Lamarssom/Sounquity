// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./ArtistSharesToken.sol";

contract ArtistSharesFactory {
    address[] public deployedTokens;
    address public platformAddress;
    address public priceFeedAddress;
    address public uniswapRouterAddress;

    mapping(string => address) public artistToToken;

    event ArtistTokenCreated(address tokenAddress, string artistId, string artistName);

    constructor(address _platformAddress, address _priceFeedAddress, address _uniswapRouterAddress) {
        platformAddress = _platformAddress;
        priceFeedAddress = _priceFeedAddress;
        uniswapRouterAddress = _uniswapRouterAddress;
    }

    function createArtistToken(
        string memory artistId,
        string memory name,
        string memory symbol,
        address teamWallet
    ) public returns (address) {
        require(artistToToken[artistId] == address(0), "Artist already registered");

        ArtistSharesToken newToken = new ArtistSharesToken(
            name,
            symbol,
            teamWallet,
            platformAddress,
            priceFeedAddress,
            uniswapRouterAddress
        );

        deployedTokens.push(address(newToken));
        artistToToken[artistId] = address(newToken);

        emit ArtistTokenCreated(address(newToken), artistId, name);
        return address(newToken);
    }

    function getTokenByArtistId(string memory artistId) public view returns (address) {
        return artistToToken[artistId];
    }

    function getDeployedTokens() public view returns (address[] memory) {
        return deployedTokens;
    }

    function getPlatformAddress() public view returns (address) {
        return platformAddress;
    }
}