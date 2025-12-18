import React from 'react';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';
import Navbar from './components/Navbar';
import Home from './pages/Home';
import Dashboard from './pages/Dashboard';
import SearchResults from './pages/SearchResults';
import ProtectedRoute from './routes/ProtectedRoute';
import ErrorBoundary from './components/ErrorBoundary';
import AdminRoute from './routes/AdminRoute';
import AdminPanel from './pages/AdminPanel';

// Dynamic imports
const ConnectWallet = React.lazy(() => import('./components/ConnectWallet'));
const ArtistDetails = React.lazy(() => import('./pages/ArtistDetails'));

function App() {
  return (
    <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Navbar />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/connectWallet" element={<ConnectWallet />} />
          <Route 
            path="/admin" 
            element={
              <AdminRoute>
               <AdminPanel />
              </AdminRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route path="/search" element={<SearchResults />} />
          <Route path="/artist-details/:contractAddress" element={<ArtistDetails />} />
        </Routes>
    </Router>
  );
}

export default App;