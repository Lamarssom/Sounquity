import React from 'react';
import { Link } from 'react-router-dom';
import ConnectWallet from './ConnectWallet';
import ErrorBoundary from './ErrorBoundary';
import styles from '../styles/Navbar.module.css';

const Navbar = () => {
  return (
    <nav className={styles.navbar}>
      <div className={styles.logo}>
        <Link to='/'>Sounquity</Link>
      </div>
      <div className={styles.links}>
        <Link to='/' className={styles.link}>
          Home
        </Link>
        <Link to='/market' className={styles.link}>
          Market
        </Link>
        <ErrorBoundary>
          <ConnectWallet />
        </ErrorBoundary>
      </div>
    </nav>
  );
};

export default Navbar;