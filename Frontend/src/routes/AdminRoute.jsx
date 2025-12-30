// src/routes/AdminRoute.jsx
import { Navigate } from "react-router-dom";
import { useAppKitAccount } from '@reown/appkit/react';

const ADMIN_WALLETS = [
  "0xd427d6782F66C62a3992Ca4fA41fF3BBc13C8579",
];

const AdminRoute = ({ children }) => {
  const { address, isConnected } = useAppKitAccount();

  const lowerAddress = address?.toLowerCase();
  const isAdmin = isConnected && lowerAddress && ADMIN_WALLETS.some(a => a.toLowerCase() === lowerAddress);

  if (!isConnected || !isAdmin) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default AdminRoute;