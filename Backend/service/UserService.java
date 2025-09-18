package com.musicinvestment.musicapp.service;

import com.musicinvestment.musicapp.model.Artist;
import com.musicinvestment.musicapp.model.User;
import com.musicinvestment.musicapp.repository.ArtistRepository;
import com.musicinvestment.musicapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final ArtistService artistService;

    public UserService(UserRepository userRepository, ArtistRepository artistRepository, ArtistService artistService) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.artistService = artistService;
    }

    public User saveUser(User user) {
        logger.info("Attempting to save user with id: {}", user.getId());
        if (userRepository.findById(user.getId()).isPresent()) {
            logger.warn("Wallet address already exists: {}", user.getId());
            throw new RuntimeException("Wallet address already exists: " + user.getId());
        }
        try {
            User savedUser = userRepository.save(user);
            logger.info("User saved successfully: id={}, balance={}", savedUser.getId(), savedUser.getBalance());
            return savedUser;
        } catch (Exception e) {
            logger.error("Failed to save user: id={}, error: {}", user.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserById(String id) {
        logger.info("Finding user by id: {}", id);
        Optional<User> user = userRepository.findByIdWithFavoriteArtists(id);
        logger.info("User find result: {}", user.isPresent() ? "found" : "not found");
        return user;
    }

    public List<User> getAllUsers() {
        logger.info("Fetching all users");
        List<User> users = userRepository.findAll();
        logger.info("Found {} users", users.size());
        return users;
    }

    public User registerUser(String walletAddress, List<String> favoriteArtistIds) {
        logger.info("Registering user with walletAddress: {}, favoriteArtistIds: {}", walletAddress, favoriteArtistIds);
        if (userRepository.findById(walletAddress).isPresent()) {
            logger.warn("Wallet address already exists: {}", walletAddress);
            throw new RuntimeException("Wallet address already exists: " + walletAddress);
        }

        User user = new User();
        user.setId(walletAddress);
        user.setBalance(BigDecimal.ZERO);

        Set<Artist> favoriteArtists = new HashSet<>();
        if (favoriteArtistIds != null && !favoriteArtistIds.isEmpty()) {
            for (String artistId : favoriteArtistIds) {
                logger.info("Fetching artist with id: {}", artistId);
                try {
                    Optional<Artist> artist = artistService.getArtistById(artistId);
                    if (artist.isPresent()) {
                        favoriteArtists.add(artist.get());
                        logger.info("Added artist to favorites: {}", artistId);
                    } else {
                        logger.warn("Artist not found: {}", artistId);
                    }
                } catch (Exception e) {
                    logger.error("Failed to fetch artist: {}, error: {}", artistId, e.getMessage(), e);
                }
            }
        }
        user.setFavoriteArtists(favoriteArtists);

        logger.info("Saving new user with id: {}", walletAddress);
        try {
            User savedUser = userRepository.save(user);
            logger.info("User registered successfully: id={}, balance={}, favoriteArtists={}", 
                        savedUser.getId(), savedUser.getBalance(), 
                        savedUser.getFavoriteArtists().stream().map(Artist::getId).collect(Collectors.toList()));
            return savedUser;
        } catch (Exception e) {
            logger.error("Failed to register user: id={}, error: {}", walletAddress, e.getMessage(), e);
            throw e;
        }
    }

    public User loginUser(String walletAddress, String signature, String message, List<String> favoriteArtistIds) {
        logger.info("Logging in user with walletAddress: {}, favoriteArtistIds: {}", walletAddress, favoriteArtistIds);
        // Verify signature using Web3j
        if (!verifySignature(walletAddress, message, signature)) {
            logger.error("Signature verification failed for walletAddress: {}", walletAddress);
            throw new RuntimeException("Invalid signature");
        }

        // Find or create user
        Optional<User> userOptional = userRepository.findByIdWithFavoriteArtists(walletAddress);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            logger.info("Existing user found: id={}, balance={}", user.getId(), user.getBalance());
            // Update favorite artists if provided
            if (favoriteArtistIds != null && !favoriteArtistIds.isEmpty()) {
                Set<Artist> favoriteArtists = new HashSet<>();
                for (String artistId : favoriteArtistIds) {
                    logger.info("Fetching artist with id: {}", artistId);
                    try {
                        Optional<Artist> artist = artistService.getArtistById(artistId);
                        if (artist.isPresent()) {
                            favoriteArtists.add(artist.get());
                            logger.info("Added artist to favorites: {}", artistId);
                        } else {
                            logger.warn("Artist not found: {}", artistId);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to fetch artist: {}, error: {}", artistId, e.getMessage(), e);
                    }
                }
                user.setFavoriteArtists(favoriteArtists);
                try {
                    user = userRepository.save(user);
                    logger.info("Updated favorite artists for user: id={}, favoriteArtists={}", 
                                user.getId(), 
                                user.getFavoriteArtists().stream().map(Artist::getId).collect(Collectors.toList()));
                } catch (Exception e) {
                    logger.error("Failed to update favorite artists for user: id={}, error: {}", 
                                 user.getId(), e.getMessage(), e);
                    throw e;
                }
            }
        } else {
            logger.info("Creating new user for walletAddress: {}", walletAddress);
            user = new User();
            user.setId(walletAddress);
            user.setBalance(BigDecimal.ZERO);
            Set<Artist> favoriteArtists = new HashSet<>();
            if (favoriteArtistIds != null && !favoriteArtistIds.isEmpty()) {
                for (String artistId : favoriteArtistIds) {
                    logger.info("Fetching artist with id: {}", artistId);
                    try {
                        Optional<Artist> artist = artistService.getArtistById(artistId);
                        if (artist.isPresent()) {
                            favoriteArtists.add(artist.get());
                            logger.info("Added artist to favorites: {}", artistId);
                        } else {
                            logger.warn("Artist not found: {}", artistId);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to fetch artist: {}, error: {}", artistId, e.getMessage(), e);
                    }
                }
            }
            user.setFavoriteArtists(favoriteArtists);
            try {
                user = userRepository.save(user);
                logger.info("New user saved: id={}, balance={}, favoriteArtists={}", 
                            user.getId(), user.getBalance(), 
                            user.getFavoriteArtists().stream().map(Artist::getId).collect(Collectors.toList()));
            } catch (Exception e) {
                logger.error("Failed to save new user: id={}, error: {}", walletAddress, e.getMessage(), e);
                throw e;
            }
        }
        return user;
    }

    private boolean verifySignature(String walletAddress, String message, String signature) {
        try {
            logger.info("Verifying signature for walletAddress: {}, message: {}", walletAddress, message);
            // Convert message to bytes (no EIP-191 prefix needed)
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            logger.debug("Message bytes: {}", Numeric.toHexString(messageBytes));

            // Remove '0x' prefix from signature if present
            String cleanSignature = signature.startsWith("0x") ? signature.substring(2) : signature;
            byte[] signatureBytes = Numeric.hexStringToByteArray(cleanSignature);

            if (signatureBytes.length != 65) {
                logger.error("Invalid signature length: {}", signatureBytes.length);
                throw new RuntimeException("Invalid signature length: " + signatureBytes.length);
            }

            // Extract r, s, v from signature
            byte[] r = new byte[32];
            byte[] s = new byte[32];
            System.arraycopy(signatureBytes, 0, r, 0, 32);
            System.arraycopy(signatureBytes, 32, s, 0, 32);
            byte v = signatureBytes[64];
            logger.debug("Signature components: v={}, r={}, s={}", v, Numeric.toHexString(r), Numeric.toHexString(s));

            // Try both v values (27 and 28)
            byte[] vValues = { v < 27 ? (byte)(v + 27) : v, (byte)(v < 27 ? v + 28 : v == 27 ? 28 : 27) };
            for (byte vVal : vValues) {
                logger.debug("Trying v={}", vVal);
                Sign.SignatureData signatureData = new Sign.SignatureData(vVal, r, s);
                BigInteger publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);
                logger.debug("Recovered public key (v={}): {}", vVal, Numeric.toHexStringWithPrefix(publicKey));

                // Derive address
                String recoveredAddress = Keys.getAddress(publicKey);
                logger.info("Recovered address (v={}): {}", vVal, "0x" + recoveredAddress);

                if (walletAddress.equalsIgnoreCase("0x" + recoveredAddress)) {
                    logger.info("Signature verification succeeded with v={}", vVal);
                    return true;
                }
            }

            logger.info("Signature verification result: false");
            return false;
        } catch (Exception e) {
            logger.error("Signature verification failed for walletAddress: {}, error: {}", walletAddress, e.getMessage(), e);
            throw new RuntimeException("Signature verification failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), e);
        }
    }

    @Override
    public UserDetails loadUserByUsername(String id) throws UsernameNotFoundException {
        logger.info("Loading user by username: {}", id);
        return userRepository.findByIdWithFavoriteArtists(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }
}