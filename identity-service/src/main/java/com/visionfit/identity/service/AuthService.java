package com.visionfit.identity.service;

import com.visionfit.identity.domain.User;
import com.visionfit.identity.dto.AuthResponse;
import com.visionfit.identity.dto.LoginRequest;
import com.visionfit.identity.dto.RefreshTokenRequest;
import com.visionfit.identity.dto.RegisterRequest;
import com.visionfit.identity.exception.ApiException;
import com.visionfit.identity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final IdentityUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            IdentityUserDetailsService userDetailsService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);

        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        User user = userDetailsService.loadUserEntityByEmail(email);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        return issueTokens(rotation.user(), rotation.refreshToken());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse issueTokens(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateToken(userDetails, user.getId());
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user);
        return AuthResponse.bearer(
                accessToken,
                refresh.plainToken(),
                jwtService.getExpirationMs(),
                refresh.expiresInMs(),
                user.getEmail()
        );
    }

    private AuthResponse issueTokens(User user, RefreshTokenService.IssuedRefreshToken refresh) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateToken(userDetails, user.getId());
        return AuthResponse.bearer(
                accessToken,
                refresh.plainToken(),
                jwtService.getExpirationMs(),
                refresh.expiresInMs(),
                user.getEmail()
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
