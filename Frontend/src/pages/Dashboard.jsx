import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchUserDetails } from "../services/api";
import "../styles/Auth.css";

const Dashboard = () => {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);

    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!token) {
            navigate("/login");
            return;
        }

        const getUserDetails = async () => {
            try {
                const data = await fetchUserDetails();
                setUser(data);
            } catch (error) {
                console.error("Error fetching user details:", error);
                navigate("/login");
            }
        };

        getUserDetails();
    }, [navigate]);

    const handleLogout = () => {
        localStorage.removeItem("token"); // Remove token
        navigate("/login"); // Redirect to login
    };

    return (
        <div style={{ textAlign: "center", padding: "20px" }}>
            <h2>Welcome to Your Dashboard</h2>
            {user ? (
                <>
                    <p>Username: {user.username}</p>
                    <p>Email: {user.email}</p>
                    <p>Balance: ${user.balance}</p>
                    <button onClick={handleLogout} style={{ marginTop: "10px", padding: "8px 16px" }}>Logout</button>
                </>
            ) : (
                <p>Loading...</p>
            )}
        </div>
    );
};

export default Dashboard;