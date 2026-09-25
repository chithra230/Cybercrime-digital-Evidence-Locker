package com.cybercrime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/registration")
@CrossOrigin(origins = "*")
public class RegistrationController {

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public RegistrationController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email already registered"));
        }

        if (userRepository.existsByPhone(user.getPhone())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Phone number already registered"));
        }

        String registrationNumber;

        do {
            registrationNumber = String.format("%06d", random.nextInt(1_000_000));
        } while (userRepository.findByRegistrationNumber(registrationNumber).isPresent());

        user.setRegistrationNumber(registrationNumber);

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful");
        response.put("registrationNumber", savedUser.getRegistrationNumber());

        return ResponseEntity.ok(response);
    }
}