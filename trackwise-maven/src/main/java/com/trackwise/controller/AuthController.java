package com.trackwise.controller;

import com.trackwise.config.JwtUtil;
import com.trackwise.dto.request.LoginRequest;
import com.trackwise.dto.request.RegisterRequest;
import com.trackwise.entity.*;
import com.trackwise.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

// ═══════════════════════════════════════════════════════════
//  AuthController  — /api/v1/auth
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Login and registration with JWT")
public class AuthController {

        private final AuthenticationManager authManager;
        private final JwtUtil jwtUtil;
        private final UserRepository userRepo;
        private final RoleRepository roleRepo;
        private final DepartmentRepository deptRepo;
        private final PasswordEncoder encoder;

        @PostMapping("/login")
        @Operation(summary = "Authenticate user and return JWT token")
        public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
                try {
                        authManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
                        User user = userRepo.findByEmail(req.getEmail()).orElseThrow();

                        // PCI DSS: reset failed logins on success
                        user.setFailedLogins((short) 0);
                        user.setLockedUntil(null);
                        userRepo.save(user);

                        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().getName());
                        log.info("LOGIN success: {}", req.getEmail());
                        return ResponseEntity.ok(Map.of(
                                        "token", token,
                                        "type", "Bearer",
                                        "userId", user.getId(),
                                        "email", user.getEmail(),
                                        "role", user.getRole().getName(),
                                        "firstName", user.getFirstName()));
                } catch (BadCredentialsException e) {
                        // PCI DSS: track failed attempts
                        userRepo.findByEmail(req.getEmail()).ifPresent(u -> {
                                int fails = u.getFailedLogins() + 1;
                                u.setFailedLogins((short) fails);
                                if (fails >= 5) {
                                        u.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                                        log.warn("Account LOCKED after {} failed attempts: {}", fails, req.getEmail());
                                }
                                userRepo.save(u);
                        });
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("error", "Invalid email or password"));
                }
        }

        @PostMapping("/register")
        @Operation(summary = "Register a new employee account")
        public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
                if (userRepo.existsByEmail(req.getEmail())) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
                }

                // Find or auto-create the role
                Role role;
                if (req.getRoleId() != null) {
                        role = roleRepo.findById(req.getRoleId()).orElseGet(() -> {
                                // role ID not found — determine name from ID
                                String roleName = req.getRoleId() == 1L ? "ADMIN"
                                                : req.getRoleId() == 2L ? "MANAGER" : "EMPLOYEE";
                                return roleRepo.findByName(roleName).orElseGet(() -> {
                                        Role r = new Role();
                                        r.setName(roleName);
                                        r.setDescription(roleName + " role");
                                        return roleRepo.save(r);
                                });
                        });
                } else {
                        role = roleRepo.findByName("EMPLOYEE").orElseGet(() -> {
                                Role r = new Role();
                                r.setName("EMPLOYEE");
                                r.setDescription("Regular employee");
                                return roleRepo.save(r);
                        });
                }

                User user = User.builder()
                                .firstName(req.getFirstName())
                                .lastName(req.getLastName())
                                .email(req.getEmail())
                                .passwordHash(encoder.encode(req.getPassword()))
                                .role(role)
                                .failedLogins((short) 0)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                if (req.getDepartmentId() != null) {
                        deptRepo.findById(req.getDepartmentId()).ifPresent(user::setDepartment);
                }
                User saved = userRepo.save(user);
                log.info("REGISTER: new user {} ({})", saved.getEmail(), role.getName());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(Map.of("userId", saved.getId(), "email", saved.getEmail(), "role",
                                                role.getName()));
        }
}

// ═══════════════════════════════════════════════════════════
// Global Exception Handler
// ═══════════════════════════════════════════════════════════
@RestControllerAdvice
class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
                Map<String, String> fieldErrors = new LinkedHashMap<>();
                for (FieldError err : ex.getBindingResult().getFieldErrors()) {
                        fieldErrors.put(err.getField(), err.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(Map.of(
                                "error", "Validation failed",
                                "fields", fieldErrors));
        }

        @ExceptionHandler(NoSuchElementException.class)
        public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Resource not found: " + ex.getMessage()));
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", ex.getMessage()));
        }
}
