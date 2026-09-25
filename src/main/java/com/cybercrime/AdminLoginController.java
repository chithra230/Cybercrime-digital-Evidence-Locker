package com.cybercrime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminLoginController {

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {

        if (request == null ||
                request.getUsername() == null ||
                request.getPassword() == null) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Username and password are required."
                    ));
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        // Admin credentials
        boolean validAdmin =
                (username.equalsIgnoreCase("admin")
                        && password.equals("admin123"))
                ||
                (username.equalsIgnoreCase("chithra honnesh")
                        && password.equals("Chithra@2007"));

        if (validAdmin) {

            Map<String, Object> response = new HashMap<>();

            response.put("message", "Admin login successful.");
            response.put("username", username);
            response.put("name", "Administrator");

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401)
                .body(Map.of(
                        "message", "Invalid admin username or password."
                ));
    }

    public static class AdminLoginRequest {

        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}