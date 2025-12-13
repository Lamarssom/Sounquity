import { useState, useEffect } from "react";
import axios from "axios";
import ArtistCard from "../components/ArtistCard"; // Adjust if your path is different
import { Spinner } from "react-bootstrap"; // Optional: You can use your spinner component here

const SearchResults = () => {
  const [searchQuery, setSearchQuery] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false); // For loading state

  // Function to fetch search results based on the query
  const fetchSearchResults = async (query) => {
    setLoading(true); // Set loading state to true
    try {
      const response = await axios.get(`http://localhost:8080/api/artists/search`, {
        params: { name: query },
      });
      setResults(response.data);
    } catch (error) {
      console.error("Error fetching search results:", error);
      setResults([]); // Optionally, clear results on error
    } finally {
      setLoading(false); // Set loading state to false
    }
  };

  // Handle search input change
  const handleSearchChange = (event) => {
    setSearchQuery(event.target.value);
  };

  // Debounce the API request to trigger only after 300ms delay
  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      if (searchQuery.length > 2) { // Trigger search after 3 characters
        fetchSearchResults(searchQuery);
      } else {
        setResults([]); // Clear results if query is empty or too short
      }
    }, 300); // Delay in ms (300ms here)

    return () => clearTimeout(delayDebounceFn); // Cleanup on searchQuery change
  }, [searchQuery]);

  return (
    <div style={styles.container}>
      <h2>Search for Artists</h2>
      <input
        type="text"
        value={searchQuery}
        onChange={handleSearchChange}
        placeholder="Search by artist name..."
        style={styles.searchInput}
      />

      {/* Loading spinner while fetching data */}
      {loading ? (
        <div style={styles.spinnerContainer}>
          <Spinner animation="border" role="status" />
        </div>
      ) : (
        <div style={styles.resultsContainer}>
          {results.length > 0 ? (
            results.map((artist) => (
              <ArtistCard key={artist.id} artist={artist} />
            ))
          ) : (
            <p>No results found</p>
          )}
        </div>
      )}
    </div>
  );
};

const styles = {
  container: {
    padding: "20px",
    textAlign: "center",
  },
  searchInput: {
    width: "300px",
    padding: "10px",
    marginBottom: "20px",
    border: "1px solid #ccc",
    borderRadius: "4px",
  },
  resultsContainer: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
    gap: "20px",
    justifyItems: "center",
  },
  spinnerContainer: {
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    height: "100px",
  },
};

export default SearchResults;