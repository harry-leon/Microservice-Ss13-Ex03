package com.rikkei.microservice.identity.service;

import com.rikkei.microservice.identity.dto.JwtResponse;
import com.rikkei.microservice.identity.dto.LoginRequest;
import com.rikkei.microservice.identity.dto.RegisterRequest;
import com.rikkei.microservice.identity.entity.User;
import com.rikkei.microservice.identity.security.JwtUtils;

public interface AuthService {
    JwtResponse authenticateUser(LoginRequest loginRequest);
    void registerUser(RegisterRequest registerRequest);
}
