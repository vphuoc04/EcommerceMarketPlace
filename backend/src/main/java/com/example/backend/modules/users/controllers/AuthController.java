package com.example.backend.modules.users.controllers;

import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.modules.users.entities.RefreshToken;
import com.example.backend.modules.users.repositories.RefreshTokenRepository;
import com.example.backend.modules.users.requests.BlacklistedTokenRequest;
import com.example.backend.modules.users.requests.LoginRequest;
import com.example.backend.modules.users.requests.RefreshTokenRequest;
import com.example.backend.modules.users.resources.LoginResource;
import com.example.backend.modules.users.resources.RefreshTokenResource;
import com.example.backend.modules.users.services.impl.BlacklistedTokenService;
import com.example.backend.modules.users.services.interfaces.UserServiceInterface;
import com.example.backend.resources.ApiResource;
import com.example.backend.services.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {
    private final UserServiceInterface userService;

    @Autowired
    private BlacklistedTokenService blacklistedTokenService;

    @Autowired 
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public AuthController(
        UserServiceInterface userService
    ){
        this.userService = userService;
    }

    @PostMapping("login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Object result = userService.authenticate(request);
        if (result instanceof LoginResource loginResource) {
            ResponseCookie cookie = ResponseCookie.from("cookies", loginResource.getToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(5)) 
                .build();

            ApiResource<LoginResource> response = ApiResource.ok(loginResource, "SUCCESS");

            return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString()) 
                .body(response);
        }
        if (result instanceof ApiResource apiResource) {
            return ResponseEntity.unprocessableEntity().body(apiResource);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Network error");
    }

    @PostMapping("blacklisted_token")
    public ResponseEntity<?> addTokenToBlacklist(@Valid @RequestBody BlacklistedTokenRequest request) {
        try {
            Object result = blacklistedTokenService.create(request);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResource.message("NETWORK_ERROR", HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @PostMapping("refresh_token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResource.message("Refresh token is invalid", HttpStatus.UNAUTHORIZED));
        }

        Optional<RefreshToken> dbRefreshTokenOptional = refreshTokenRepository.findByRefreshToken(refreshToken);

        if(dbRefreshTokenOptional.isPresent()) {
            RefreshToken dbRefreshToken = dbRefreshTokenOptional.get();
            Long userId = dbRefreshToken.getUserId();
            String email = dbRefreshToken.getUser().getEmail();

            String newToken = jwtService.generateToken(userId, email, null);
            String newRefreshToken = jwtService.generateRefreshToken(userId, email);

            ApiResource<RefreshTokenResource> response = ApiResource.ok(new RefreshTokenResource(newToken, newRefreshToken), "SUCCESS");

            return ResponseEntity.ok(response);
        }
        return ResponseEntity.internalServerError().body(ApiResource.message("NETWORK_ERROR", HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
