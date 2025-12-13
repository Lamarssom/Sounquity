// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract ArtistPriceCalculator {
    struct Artist {
        uint256 debutListeners;
        uint256 pricePerListenerUnit;
        uint256 currentListeners;
        uint256 songsOver1BStreams;
        uint256 totalMilestones;
        uint256 lastUpdated;
    }

    mapping(address => Artist) public artists;
    
    uint256 public growthWeight = 10; // Example growth weight for every 10 million listeners
    uint256 public boostPerSong = 0.50 ether; // Boost per song over 1B streams
    uint256 public bonusPerMilestone = 2 ether; // Bonus per milestone

    // Initialize base price using debut listeners and price per listener unit
    function initializeBasePrice(address artist, uint256 debutListeners, uint256 pricePerListenerUnit) public {
        require(debutListeners > 0, "Debut listeners must be greater than 0");
        require(pricePerListenerUnit > 0, "Price per listener unit must be greater than 0");

        artists[artist] = Artist({
            debutListeners: debutListeners,
            pricePerListenerUnit: pricePerListenerUnit,
            currentListeners: debutListeners,
            songsOver1BStreams: 0,
            totalMilestones: 0,
            lastUpdated: block.timestamp
        });
    }

    // Update current listeners
    function updateListeners(address artist, uint256 newListeners) public {
        require(newListeners > 0, "New listeners count must be greater than 0");
        artists[artist].currentListeners = newListeners;
        artists[artist].lastUpdated = block.timestamp;
    }

    // Update number of songs with over 1B streams
    function updateStreamBoost(address artist, uint256 newSongCount) public {
        artists[artist].songsOver1BStreams = newSongCount;
        artists[artist].lastUpdated = block.timestamp;
    }

    // Update milestones
    function updateMilestones(address artist, uint256 milestoneCount) public {
        artists[artist].totalMilestones = milestoneCount;
        artists[artist].lastUpdated = block.timestamp;
    }

    // Calculate current share price for an artist
    function calculateCurrentPrice(address artist) public view returns (uint256) {
        Artist memory artistData = artists[artist];

        uint256 basePrice = artistData.debutListeners * artistData.pricePerListenerUnit;
        uint256 listenerGrowthFactor = ((artistData.currentListeners - artistData.debutListeners) * growthWeight) / 10**6; // Adjusting growth based on listeners
        uint256 streamBoost = artistData.songsOver1BStreams * boostPerSong;
        uint256 milestoneBonus = artistData.totalMilestones * bonusPerMilestone;

        uint256 currentPrice = basePrice + listenerGrowthFactor + streamBoost + milestoneBonus;
        return currentPrice;
    }

    // Optional: Time decay function can be added to account for older data
    function calculateTimeDecay(address artist) public view returns (uint256) {
        uint256 timeElapsed = block.timestamp - artists[artist].lastUpdated;
        uint256 decayFactor = timeElapsed / 1 days; // Simple decay factor, can adjust logic based on needs
        return decayFactor;
    }
}