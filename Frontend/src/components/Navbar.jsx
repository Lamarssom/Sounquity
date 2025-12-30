// src/components/Navbar.jsx
import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import ConnectWallet from "./ConnectWallet";
import ErrorBoundary from "./ErrorBoundary";
import styles from "../styles/Navbar.module.css";

const ADMIN_WALLETS = [
  "0xd427d6782F66C62a3992Ca4fA41fF3BBc13C8579",
];

const getCurrentWallet = () => {
  try {
    const cache = localStorage.getItem("@appkit/identity_cache");
    if (cache && cache !== "null" && cache !== "{}") {
      const parsed = JSON.parse(cache);
      const address = Object.keys(parsed)[0];
      if (address) return address.toLowerCase();
    }
    return null;
  } catch (err) {
    return null;
  }
};

const Navbar = () => {
  const { address, isConnected } = useAppKitAccount();
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    if (isConnected && address) {
      const lowerAddress = address.toLowerCase();
      const allowed = ADMIN_WALLETS.some(a => a.toLowerCase() === lowerAddress);
      setIsAdmin(allowed);
    } else {
      setIsAdmin(false);
    }
  }, [isConnected, address]);

  return (
    <nav className={styles.navbar}>
      <div className={styles.logo}>
        <Link to="/">Sounquity</Link>
      </div>
      <div className={styles.links}>
        <Link to="/" className={styles.link}>Home</Link>

        {/* ADMIN BUTTON — DISAPPEARS INSTANTLY ON DISCONNECT */}
        {isAdmin && (
          <Link
            to="/admin"
            className={styles.link}
            style={{
              background: "linear-gradient(45deg, #FFD700, #FFA500)",
              color: "black",
              padding: "12px 28px",
              borderRadius: "12px",
              fontWeight: "900",
              fontSize: "1.1rem",
              boxShadow: "0 0 25px rgba(255,215,0,0.9)",
              animation: "pulse 2s infinite",
              textDecoration: "none",
              display: "inline-block",
              textAlign: "center",
            }}
          >
            ADMIN PANEL
          </Link>
        )}

        <ErrorBoundary>
          <ConnectWallet />
        </ErrorBoundary>
      </div>
    </nav>
  );
};

export default Navbar;