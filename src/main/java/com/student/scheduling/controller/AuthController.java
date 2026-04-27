package com.student.scheduling.controller;

import com.student.scheduling.entity.User;
import com.student.scheduling.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public endpoints for account signup, login, and password reset")
public class AuthController {

    private static final long RESET_TOKEN_VALIDITY_MS = 15 * 60 * 1000;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private com.student.scheduling.security.JwtUtil jwtUtil;

    @Value("${app.frontend.base-url:${APP_FRONTEND_BASE_URL:http://localhost:5173}}")
    private String frontendBaseUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @PostMapping("/signup")
    @Operation(summary = "Sign up", description = "Public endpoint that creates a student or admin account and returns a JWT token.")
    public ResponseEntity<?> signup(@RequestBody User user) {
        String normalizedRole = normalizeRole(user.getRole());
        if (normalizedRole == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only student and admin accounts are allowed"));
        }
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }
        user.setRole(normalizedRole);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
            "user", publicUser(user),
            "token", jwtUtil.generateToken(user)
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Public endpoint that validates credentials and returns a JWT token.")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            User user = userOpt.get();
            String normalizedRole = normalizeRole(user.getRole());
            if (normalizedRole == null) {
                return ResponseEntity.status(403).body(Map.of("message", "This account role is no longer supported"));
            }
            if (!normalizedRole.equals(user.getRole())) {
                user.setRole(normalizedRole);
                userRepository.save(user);
            }
            return ResponseEntity.ok(Map.of(
                "user", publicUser(user),
                "token", jwtUtil.generateToken(user)
            ));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Public endpoint that sends a password reset link when the email exists.")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String identifier = body.get("identifier");
        if ((identifier == null || identifier.isBlank()) && body.get("email") != null) {
            identifier = body.get("email");
        }
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        String normalizedIdentifier = identifier.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmail(normalizedIdentifier);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                user.setResetToken(UUID.randomUUID().toString());
                user.setResetTokenExpiry(System.currentTimeMillis() + RESET_TOKEN_VALIDITY_MS);
                userRepository.save(user);
                sendPasswordResetEmail(user);
            }
        }

        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Public endpoint that updates a password using a valid reset token.")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || token.isBlank() || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing required fields"));
        }
        if (newPassword.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 4 characters"));
        }

        Optional<User> userOpt = userRepository.findByResetToken(token.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid reset link"));
        }

        User user = userOpt.get();
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry() < System.currentTimeMillis()) {
            return ResponseEntity.badRequest().body(Map.of("message", "This reset link has expired. Please request a new one."));
        }

        user.setPassword(newPassword);
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalizedRole = role.trim().toLowerCase();
        if ("student".equals(normalizedRole) || "admin".equals(normalizedRole)) {
            return normalizedRole;
        }
        if ("instructor".equals(normalizedRole)) {
            return "admin";
        }
        return null;
    }

    private Map<String, Object> publicUser(User user) {
        return Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole(),
            "email", user.getEmail() == null ? "" : user.getEmail()
        );
    }

    private void sendPasswordResetEmail(User user) {
        String resetLink = UriComponentsBuilder
            .fromUriString(frontendBaseUrl.trim())
            .pathSegment("forgot-password")
            .queryParam("token", user.getResetToken())
            .build()
            .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(user.getEmail().trim().toLowerCase(Locale.ROOT));
        message.setSubject("CourseCrafter Password Reset");
        message.setText(
            "Hello " + user.getUsername() + ",\n\n" +
            "We received a request to reset your CourseCrafter password.\n" +
            "Use the link below to set a new password:\n\n" +
            resetLink + "\n\n" +
            "This link will expire in 15 minutes.\n" +
            "If you did not request this, you can ignore this email."
        );

        mailSender.send(message);
    }
}
