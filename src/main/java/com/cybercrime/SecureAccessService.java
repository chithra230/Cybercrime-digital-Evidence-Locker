package com.cybercrime;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecureAccessService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final SecureAccessTokenRepository secureAccessTokenRepository;
    private final JavaMailSender mailSender;

    @Value("${cybercrime.otp.expiry-seconds:30}")
    private long otpExpirySeconds;

    @Value("${cybercrime.secure-token.expiry-hours:24}")
    private long secureTokenExpiryHours;

    public SecureAccessService(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            SecureAccessTokenRepository secureAccessTokenRepository,
            JavaMailSender mailSender) {

        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.secureAccessTokenRepository = secureAccessTokenRepository;
        this.mailSender = mailSender;
    }


    // =========================================================
    // SEND OTP
    // =========================================================

    @Transactional
    public String sendOtp(
            String complaintId,
            String registrationNumber,
            String email) {

        if (isBlank(complaintId)) {
            throw new RuntimeException(
                    "Complaint ID is required."
            );
        }

        if (isBlank(registrationNumber)) {
            throw new RuntimeException(
                    "Registration number is required."
            );
        }

        if (isBlank(email)) {
            throw new RuntimeException(
                    "Email is required."
            );
        }


        // Find registered user
        Optional<User> userOptional =
                userRepository.findByRegistrationNumber(
                        registrationNumber.trim()
                );

        if (userOptional.isEmpty()) {
            throw new RuntimeException(
                    "Registration number not found."
            );
        }

        User user = userOptional.get();


        // Check registered email
        if (user.getEmail() == null ||
                !user.getEmail()
                        .trim()
                        .equalsIgnoreCase(
                                email.trim()
                        )) {

            throw new RuntimeException(
                    "The entered email does not match the registered email."
            );
        }


        // Delete previous OTP
        otpVerificationRepository
                .deleteByComplaintIdAndEmail(
                        complaintId.trim(),
                        email.trim()
                );


        // Generate 6 digit OTP
        SecureRandom random =
                new SecureRandom();

        int otpNumber =
                100000 + random.nextInt(900000);

        String otp =
                String.valueOf(otpNumber);


        // Hash OTP
        String otpHash =
                sha256(otp);


        // Create OTP record
        OtpVerification otpVerification =
                new OtpVerification();

        otpVerification.setComplaintId(
                complaintId.trim()
        );

        otpVerification.setVictimRegistrationNumber(
                registrationNumber.trim()
        );

        otpVerification.setEmail(
                email.trim()
        );

        otpVerification.setOtpHash(
                otpHash
        );

        otpVerification.setCreatedAt(
                LocalDateTime.now()
        );

        otpVerification.setExpiresAt(
                LocalDateTime.now()
                        .plusSeconds(
                                otpExpirySeconds
                        )
        );

        otpVerification.setUsed(false);


        otpVerificationRepository.save(
                otpVerification
        );


        // =====================================================
        // SEND EMAIL
        // =====================================================

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(
                email.trim()
        );

        message.setSubject(
                "Cybercrime Secure Access OTP"
        );

        message.setText(
                "Your Cybercrime Digital Evidence Management System OTP is: "
                        + otp
                        + "\n\n"
                        + "Complaint ID: "
                        + complaintId
                        + "\n\n"
                        + "This OTP is valid for "
                        + otpExpirySeconds
                        + " seconds."
                        + "\n\n"
                        + "Do not share this OTP with anyone."
        );


        mailSender.send(message);


        return "OTP sent successfully.";
    }


    // =========================================================
    // VERIFY OTP
    // =========================================================

    @Transactional
    public String verifyOtp(
            String complaintId,
            String registrationNumber,
            String email,
            String otp) {

        if (isBlank(complaintId)) {
            throw new RuntimeException(
                    "Complaint ID is required."
            );
        }

        if (isBlank(registrationNumber)) {
            throw new RuntimeException(
                    "Registration number is required."
            );
        }

        if (isBlank(email)) {
            throw new RuntimeException(
                    "Email is required."
            );
        }

        if (isBlank(otp)) {
            throw new RuntimeException(
                    "OTP is required."
            );
        }


        Optional<OtpVerification> otpOptional =
                otpVerificationRepository
                        .findTopByComplaintIdAndEmailAndUsedFalseOrderByCreatedAtDesc(
                                complaintId.trim(),
                                email.trim()
                        );


        if (otpOptional.isEmpty()) {

            throw new RuntimeException(
                    "No valid OTP found. Please request a new OTP."
            );
        }


        OtpVerification otpVerification =
                otpOptional.get();


        // Check registration number
        if (!registrationNumber
                .trim()
                .equalsIgnoreCase(
                        otpVerification
                                .getVictimRegistrationNumber()
                )) {

            throw new RuntimeException(
                    "Invalid registration number."
            );
        }


        // Check expiry
        if (otpVerification
                .getExpiresAt()
                .isBefore(
                        LocalDateTime.now()
                )) {

            throw new RuntimeException(
                    "OTP has expired. Please request a new OTP."
            );
        }


        // Check OTP
        String enteredOtpHash =
                sha256(
                        otp.trim()
                );


        if (!enteredOtpHash.equals(
                otpVerification.getOtpHash()
        )) {

            throw new RuntimeException(
                    "Invalid OTP."
            );
        }


        // Mark OTP used
        otpVerification.setUsed(true);

        otpVerificationRepository.save(
                otpVerification
        );


        // Delete previous secure tokens
        secureAccessTokenRepository
                .deleteByComplaintIdAndVictimRegistrationNumber(
                        complaintId.trim(),
                        registrationNumber.trim()
                );


        // =====================================================
        // CREATE SECURE ACCESS TOKEN
        // =====================================================

        String token =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");


        SecureAccessToken accessToken =
                new SecureAccessToken();

        accessToken.setToken(
                token
        );

        accessToken.setComplaintId(
                complaintId.trim()
        );

        accessToken.setVictimRegistrationNumber(
                registrationNumber.trim()
        );

        accessToken.setEmail(
                email.trim()
        );

        accessToken.setCreatedAt(
                LocalDateTime.now()
        );

        accessToken.setExpiresAt(
                LocalDateTime.now()
                        .plusHours(
                                secureTokenExpiryHours
                        )
        );


        secureAccessTokenRepository.save(
                accessToken
        );


        return token;
    }


    // =========================================================
    // VALIDATE SECURE ACCESS TOKEN
    // =========================================================

    public boolean validateAccessToken(
            String token,
            String complaintId,
            String registrationNumber) {

        if (isBlank(token) ||
                isBlank(complaintId) ||
                isBlank(registrationNumber)) {

            return false;
        }


        Optional<SecureAccessToken> tokenOptional =
                secureAccessTokenRepository
                        .findByToken(
                                token.trim()
                        );


        if (tokenOptional.isEmpty()) {
            return false;
        }


        SecureAccessToken accessToken =
                tokenOptional.get();


        // Check complaint
        if (!complaintId
                .trim()
                .equalsIgnoreCase(
                        accessToken.getComplaintId()
                )) {

            return false;
        }


        // Check registration number
        if (!registrationNumber
                .trim()
                .equalsIgnoreCase(
                        accessToken
                                .getVictimRegistrationNumber()
                )) {

            return false;
        }


        // Check expiry
        if (accessToken
                .getExpiresAt()
                .isBefore(
                        LocalDateTime.now()
                )) {

            return false;
        }


        return true;
    }


    // =========================================================
    // SHA-256
    // =========================================================

    private String sha256(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to process secure data.",
                    e
            );
        }
    }


    // =========================================================
    // BLANK CHECK
    // =========================================================

    private boolean isBlank(String value) {

        return value == null ||
                value.trim().isEmpty();
    }
}