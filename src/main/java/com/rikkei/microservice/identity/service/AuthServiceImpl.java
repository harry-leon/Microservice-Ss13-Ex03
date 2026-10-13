package com.rikkei.microservice.identity.service;

import com.rikkei.microservice.identity.dto.JwtResponse;
import com.rikkei.microservice.identity.dto.LoginRequest;
import com.rikkei.microservice.identity.dto.RegisterRequest;
import com.rikkei.microservice.identity.entity.User;
import com.rikkei.microservice.identity.repository.UserRepository;
import com.rikkei.microservice.identity.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        // Query User from Database via UserRepository
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        // Match password using PasswordEncoder.matches(rawPassword, encodedPassword)
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }

        // Generate JWT token
        String jwt = jwtUtils.generateAccessToken(user);

        return JwtResponse.builder()
                .token(jwt)
                .accessToken(jwt)
                .tokenType("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    @Override
    public void registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new IllegalArgumentException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        Set<String> roles = registerRequest.getRoles();
        if (roles == null || roles.isEmpty()) {
            roles = new HashSet<>();
            roles.add("USER");
        }

        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .roles(roles)
                .build();

        userRepository.save(user);
    }
}
