import React, { useState } from "react";
import { loginUser } from "../services/api";
import { useNavigate } from "react-router-dom";
import "../styles/Auth.css";

const Login = () => {
    const [formData, setFormData] = useState({ usernameOrEmail: "", password: "" });
    const [error, setError] = useState(null);
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);

        try {
            const data = await loginUser(formData); // loginUser now returns parsed JSON

            if (data.token) {
                localStorage.setItem("token", data.token);
                localStorage.setItem("userId", data.userId); // Store userId
                console.log("Token and userId stored successfully");

                // Redirect to dashboard
                navigate("/dashboard");
            } else {
                console.warn("No token received");
            }
        } catch (err) {
            console.error("Login error:", err.message);
            setError(err.message);
        }
    };

    return (
        <div className="auth-container">
            <h2>Login</h2>
            {error && <p className="error">{error}</p>}

            <form onSubmit={handleSubmit} className="auth-form">
                <input type="text" name="usernameOrEmail" placeholder="Email or Username" value={formData.usernameOrEmail} onChange={handleChange} required />
                <input type="password" name="password" placeholder="Password" value={formData.password} onChange={handleChange} required />
                <button type="submit" className="auth-btn">Login</button>
            </form>
        </div>
    );
};

export default Login;