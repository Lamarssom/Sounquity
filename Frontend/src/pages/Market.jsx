import React, { useState, useEffect, useCallback } from 'react';
import { getWeb3, getArtistTokenDetails, deployArtistContract } from "../utilities/web3";
import ArtistSharesTokenABI from "../abis/ArtistSharesTokenABI.json";
import axios from "axios";
import "../styles/Auth.css";

const Marketplace = () => {
  const [web3, setWeb3] = useState(null);
  const [walletAddress, setWalletAddress] = useState("");
  const [balance, setBalance] = useState("");
  const [artistContracts, setArtistContracts] = useState([]);
  const [selectedArtist, setSelectedArtist] = useState(null);
  const [amount, setAmount] = useState(0);
  const [sharePrice, setSharePrice] = useState(0.01);
  const [action, setAction] = useState("buy");
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const fetchArtistContracts = useCallback(async () => {
    try {
      const jwtToken = localStorage.getItem("jwtToken");
      const response = await axios.get("http://localhost:8080/api/artists", {
        headers: { Authorization: `Bearer ${jwtToken}` }
      });

      const contracts = await Promise.all(response.data.map(async (artist) => {
        if (!artist.contractAddress) {
          const deployedAddress = await deployArtistContract(artist);
          if (!deployedAddress) return null;

          const details = await getArtistTokenDetails(deployedAddress, walletAddress);
          return {
            ...artist,
            ...details,
            contractAddress: deployedAddress,
          };
        } else {
          const details = await getArtistTokenDetails(artist.contractAddress, walletAddress);
          return {
            ...artist,
            ...details,
          };
        }
      }));

      setArtistContracts(contracts.filter(a => a));
    } catch (error) {
      console.error("Error fetching artist contracts:", error);
    }
  }, [walletAddress]);

  useEffect(() => {
    const initializeWeb3 = async () => {
      try {
        const web3Instance = getWeb3();
        setWeb3(web3Instance);

        const accounts = await web3Instance.eth.requestAccounts();
        setWalletAddress(accounts[0]);

        const bal = await web3Instance.eth.getBalance(accounts[0]);
        setBalance(web3Instance.utils.fromWei(bal, "ether"));

        await fetchArtistContracts();
      } catch (err) {
        console.error("Web3 init error:", err);
      }
    };

    initializeWeb3();
  }, [fetchArtistContracts]);

  const handleArtistSelect = (artist) => {
    setSelectedArtist(artist);
    setErrorMessage("");
    setSuccessMessage("");
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage("");
    setSuccessMessage("");

    if (!web3 || !selectedArtist) return;

    try {
      const contract = new web3.eth.Contract(ArtistSharesTokenABI, selectedArtist.contractAddress);
      const priceInWei = web3.utils.toWei(sharePrice.toString(), 'ether');

      let tx;
      if (action === "buy") {
        tx = await contract.methods.buyShares(amount).send({
          from: walletAddress,
          value: priceInWei
        });
        setSuccessMessage(`Bought ${amount} shares. Tx: ${tx.transactionHash}`);
      } else if (action === "sell") {
        tx = await contract.methods.sellShares(amount).send({ from: walletAddress });
        setSuccessMessage(`Sold ${amount} shares. Tx: ${tx.transactionHash}`);
      } else if (action === "list") {
        tx = await contract.methods.listSharesForSale(amount, priceInWei).send({ from: walletAddress });
        setSuccessMessage(`Listed ${amount} shares. Tx: ${tx.transactionHash}`);
      }

      const updatedDetails = await getArtistTokenDetails(selectedArtist.contractAddress, walletAddress);
      setSelectedArtist({ ...selectedArtist, ...updatedDetails });
      fetchArtistContracts(); // Refresh cards
    } catch (err) {
      setErrorMessage(err.message || "Transaction failed");
    }
  };

  return (
    <div>
      <h1>Marketplace</h1>

      <div style={{ marginBottom: "20px", padding: "10px", backgroundColor: "#e7f3ff", borderRadius: "8px" }}>
        <p><strong>Wallet Address:</strong> {walletAddress}</p>
        <p><strong>Wallet Balance:</strong> {balance} BNB</p>
        {selectedArtist && (
          <p style={{ marginTop: "10px", fontSize: "1.1em" }}>
            <strong>Your {selectedArtist.symbol} Balance:</strong> {selectedArtist.userBalance || 0} shares
          </p>
        )}
      </div>

      {errorMessage && <p style={{ color: "red" }}>{errorMessage}</p>}
      {successMessage && <p style={{ color: "green" }}>{successMessage}</p>}

      <div className="artist-cards">
        {artistContracts.map((artist) => (
          <div
            key={artist.id}
            className="artist-card"
            style={{
              border: selectedArtist?.id === artist.id ? "2px solid green" : "1px solid #ccc",
              padding: "10px",
              margin: "10px",
              cursor: "pointer",
            }}
            onClick={() => handleArtistSelect(artist)}
          >
            <h3>{artist.name} ({artist.symbol})</h3>
            <p>Price: {artist.price} BNB</p>
            <p>Volume: {artist.volume}</p>
          </div>
        ))}
      </div>

      {selectedArtist && (
        <div style={{ marginTop: "20px", padding: "10px", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#f9f9f9" }}>
          <h2>Trading {selectedArtist.name} ({selectedArtist.symbol})</h2>
          <p><strong>Price per Share:</strong> {selectedArtist.price} BNB</p>
          <p><strong>Available Volume:</strong> {selectedArtist.volume}</p>
        </div>
      )}

      {selectedArtist && (
        <form onSubmit={handleSubmit} style={{ marginTop: "30px" }}>
          <h2>Trade Shares of {selectedArtist.name}</h2>
          <div>
            <label>Action:</label>
            <select value={action} onChange={(e) => setAction(e.target.value)}>
              <option value="buy">Buy</option>
              <option value="sell">Sell</option>
              <option value="list">List for Sale</option>
            </select>
          </div>
          <div>
            <label>Amount:</label>
            <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          </div>
          {action === "list" && (
            <div>
              <label>Price per Share (BNB):</label>
              <input type="number" value={sharePrice} onChange={(e) => setSharePrice(e.target.value)} required />
            </div>
          )}
          <button type="submit">
            {action === "buy" ? "Buy" : action === "sell" ? "Sell" : "List"}
          </button>
        </form>
      )}
    </div>
  );
};

export default Marketplace;