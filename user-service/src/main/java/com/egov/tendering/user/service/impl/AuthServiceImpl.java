package com.egov.tendering.user.service.impl;


import com.egov.tendering.user.dal.dto.AuthResponse;
import com.egov.tendering.user.dal.dto.LoginRequest;
import com.egov.tendering.user.dal.model.User;
import com.egov.tendering.user.dal.repository.UserRepository;
import com.egov.tendering.user.event.UserEventPublisher;
import com.egov.tendering.user.exception.InvalidCredentialsException;
import com.egov.tendering.user.security.JwtTokenProvider;
import com.egov.tendering.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Logging in user: {}", request.getUsernameOrEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = tokenProvider.generateToken(authentication);

            User user = userRepository.findByUsernameOrEmail(
                            request.getUsernameOrEmail(),
                            request.getUsernameOrEmail())
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));

            // Publish login event
            eventPublisher.publishUserLoginEvent(user);

            return AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole())
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", request.getUsernameOrEmail());
            throw new InvalidCredentialsException("Invalid username/email or password");
        }
    }

    @Override
    public String generateToken(String username) {
        return tokenProvider.generateToken(username);
    }

    @Override
    public boolean validateToken(String token) {
        return tokenProvider.validateToken(token);
    }

    @Override
    public String getUsernameFromToken(String token) {
        return tokenProvider.getUsernameFromToken(token);
    }
}