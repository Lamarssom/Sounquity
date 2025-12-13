// src/utilities/auth.js
export const getAuthToken = () => localStorage.getItem("jwtToken");

export const getAuthHeaders = () => {
    const token = getAuthToken();
    return token ? { Authorization: `Bearer ${token}`} : {};
};