package com.ijse.eca.users.service;

import com.ijse.eca.users.api.dto.AuthRequest;
import com.ijse.eca.users.api.dto.AuthResponse;
import com.ijse.eca.users.api.dto.RegisterRequest;
import com.ijse.eca.users.domain.User;
import com.ijse.eca.users.repo.UserRepository;
import com.ijse.eca.users.web.ConflictException;
import com.ijse.eca.users.web.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        String passwordHash = passwordHasher.sha256Base64(request.password());
        User saved = userRepository.save(new User(request.name(), request.email(), passwordHash));
        return new AuthResponse(jwtService.issueToken(saved.getId(), saved.getEmail()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        String hash = passwordHasher.sha256Base64(request.password());
        if (!hash.equals(user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return new AuthResponse(jwtService.issueToken(user.getId(), user.getEmail()));
    }
}
