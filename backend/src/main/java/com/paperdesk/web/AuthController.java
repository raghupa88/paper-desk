package com.paperdesk.web;

import com.paperdesk.config.JwtService;
import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.User;
import com.paperdesk.repo.UserRepo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SignupRequest(@Email @NotBlank String email, @Size(min = 6) String password,
                                @NotBlank String displayName, Role role) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepo users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody SignupRequest req) {
        users.findByEmail(req.email()).ifPresent(u -> {
            throw new IllegalArgumentException("An account with this email already exists");
        });
        if (req.password() == null || req.password().length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters");
        User user = new User();
        user.email = req.email();
        user.passwordHash = encoder.encode(req.password());
        user.displayName = req.displayName();
        user.role = req.role() == null ? Role.STUDENT : req.role();
        users.save(user);
        return tokenResponse(user);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email())
                .filter(u -> encoder.matches(req.password(), u.passwordHash))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        return tokenResponse(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        User user = users.findById(SecurityConfig.currentUserId()).orElseThrow();
        return userJson(user);
    }

    private Map<String, Object> tokenResponse(User user) {
        return Map.of("token", jwt.issue(user), "user", userJson(user));
    }

    private Map<String, Object> userJson(User user) {
        return Map.of("id", user.id, "email", user.email, "displayName", user.displayName, "role", user.role.name());
    }
}
