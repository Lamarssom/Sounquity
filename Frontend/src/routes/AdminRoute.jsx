// src/routes/AdminRoute.jsx
import { useState, useEffect } from "react";
import { Navigate, useLocation } from "react-router-dom";

const ADMIN_WALLETS = [
  "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
  "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
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

const AdminRoute = ({ children }) => {
  const [isAdmin, setIsAdmin] = useState(null);
  const location = useLocation();

  useEffect(() => {
    const check = () => {
      const wallet = getCurrentWallet();
      const admin = wallet && ADMIN_WALLETS.map(a => a.toLowerCase()).includes(wallet);
      setIsAdmin(admin);
    };

    check();
    const interval = setInterval(check, 1000);

    // Instant redirect if cache is cleared (disconnect)
    const handleStorage = (e) => {
      if (e.key === "@appkit/identity_cache" || e.key === null) {
        check();
      }
    };

    window.addEventListener("storage", handleStorage);

    // Monkey patch for immediate reaction
    const originalRemoveItem = localStorage.removeItem;
    localStorage.removeItem = function(key) {
      originalRemoveItem.apply(this, arguments);
      if (key === "@appkit/identity_cache") {
        setIsAdmin(false);
      }
    };

    return () => {
      clearInterval(interval);
      window.removeEventListener("storage", handleStorage);
    };
  }, []);

  // If we detect disconnect while on /admin → redirect instantly
  if (isAdmin === false && location.pathname === "/admin") {
    return <Navigate to="/" replace />;
  }

  // Still loading
  if (isAdmin === null) {
    return (
      <div style={{
        height: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#111",
        color: "var(--sounquity-yellow)",
        fontSize: "1.8rem",
        flexDirection: "column",
        gap: "1rem"
      }}>
        <div className="loading-spinner"></div>
        <p>Checking Admin Access...</p>
      </div>
    );
  }

  // Not admin → go home
  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  // Admin → show panel
  return children;
};

export default AdminRoute;