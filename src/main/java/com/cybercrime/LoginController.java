package com.cybercrime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/login")
@CrossOrigin(origins = "*")
public class LoginController {

    private final UserRepository userRepository;

    public LoginController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        Optional<User> userOptional =
                userRepository.findByRegistrationNumber(
                        request.getRegistrationNumber()
                );

        if (userOptional.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            "Invalid registration number or password"
                    ));
        }

        User user = userOptional.get();

        if (!user.getPassword().equals(request.getPassword())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            "Invalid registration number or password"
                    ));
        }

        Map<String, Object> response = new HashMap<>();

        response.put("message", "Login successful");
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put(
                "registrationNumber",
                user.getRegistrationNumber()
        );

        return ResponseEntity.ok(response);
    }


    public static class LoginRequest {

        private String registrationNumber;
        private String password;

        public LoginRequest() {
        }

        public String getRegistrationNumber() {
            return registrationNumber;
        }

        public void setRegistrationNumber(String registrationNumber) {
            this.registrationNumber =
                    registrationNumber;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}