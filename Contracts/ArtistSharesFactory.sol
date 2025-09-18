// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./ArtistSharesToken.sol";

contract ArtistSharesFactory {
    address[] public deployedTokens;
    address public platformAddress;
    address public priceFeedAddress;

    mapping(string => address) public artistToToken;

    event ArtistTokenCreated(address tokenAddress, string artistId, string artistName);

    constructor(address _platformAddress, address _priceFeedAddress) {
        platformAddress = _platformAddress;
        priceFeedAddress = _priceFeedAddress;
    }

    function createArtistToken(
        string memory artistId,
        string memory name,
        string memory symbol,
        address teamWallet,
        uint256 popularity
    ) public returns (address) {
        require(artistToToken[artistId] == address(0), "Artist already registered");
        require(popularity <= 100, "Popularity must be 0-100");

        uint256 initialSupply = 1_000_000_000 * 10**18; // 1 billion tokens
        uint256 basePrice = 0.001 ether; // $3.50 at $3,500/ETH

        ArtistSharesToken newToken = new ArtistSharesToken(
            name,
            symbol,
            initialSupply,
            basePrice,
            platformAddress,
            teamWallet,
            popularity,
            priceFeedAddress
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