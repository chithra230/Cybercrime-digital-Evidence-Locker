package com.cybercrime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/secure")
@CrossOrigin(origins = "*")
public class SecureComplaintController {

    private final ComplaintRepository complaintRepository;
    private final EvidenceRepository evidenceRepository;
    private final SecureAccessService secureAccessService;
    private final SecureAccessTokenRepository secureAccessTokenRepository;


    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public SecureComplaintController(
            ComplaintRepository complaintRepository,
            EvidenceRepository evidenceRepository,
            SecureAccessService secureAccessService,
            SecureAccessTokenRepository secureAccessTokenRepository) {

        this.complaintRepository =
                complaintRepository;

        this.evidenceRepository =
                evidenceRepository;

        this.secureAccessService =
                secureAccessService;

        this.secureAccessTokenRepository =
                secureAccessTokenRepository;
    }


    // =========================================================
    // SEND OTP
    // =========================================================

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(
            @RequestBody OtpRequest request) {

        if (request == null ||
                isBlank(request.complaintId) ||
                isBlank(request.registrationNumber) ||
                isBlank(request.email)) {

            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint ID, registration number and email are required."
                            )
                    );
        }


        Optional<Complaint> complaintOptional =
                complaintRepository.findByComplaintId(
                        request.complaintId.trim()
                );


        if (complaintOptional.isEmpty()) {

            return ResponseEntity.status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint not found."
                            )
                    );
        }


        Complaint complaint =
                complaintOptional.get();


        if (!request.registrationNumber
                .trim()
                .equalsIgnoreCase(
                        complaint
                                .getVictimRegistrationNumber()
                )) {

            return ResponseEntity.status(403)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint does not belong to this registration number."
                            )
                    );
        }


        try {

            String message =
                    secureAccessService.sendOtp(
                            request.complaintId.trim(),
                            request.registrationNumber.trim(),
                            request.email.trim()
                    );


            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            message
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    e.getMessage() == null
                                            ? "Unable to send OTP."
                                            : e.getMessage()
                            )
                    );
        }
    }


    // =========================================================
    // VERIFY OTP
    // =========================================================

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody VerifyOtpRequest request) {

        if (request == null ||
                isBlank(request.complaintId) ||
                isBlank(request.registrationNumber) ||
                isBlank(request.email) ||
                isBlank(request.otp)) {

            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint ID, registration number, email and OTP are required."
                            )
                    );
        }


        Optional<Complaint> complaintOptional =
                complaintRepository.findByComplaintId(
                        request.complaintId.trim()
                );


        if (complaintOptional.isEmpty()) {

            return ResponseEntity.status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint not found."
                            )
                    );
        }


        Complaint complaint =
                complaintOptional.get();


        if (!request.registrationNumber
                .trim()
                .equalsIgnoreCase(
                        complaint
                                .getVictimRegistrationNumber()
                )) {

            return ResponseEntity.status(403)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint does not belong to this registration number."
                            )
                    );
        }


        try {

            String accessToken =
                    secureAccessService.verifyOtp(
                            request.complaintId.trim(),
                            request.registrationNumber.trim(),
                            request.email.trim(),
                            request.otp.trim()
                    );


            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "OTP verified successfully.",
                            "accessToken",
                            accessToken
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    e.getMessage() == null
                                            ? "OTP verification failed."
                                            : e.getMessage()
                            )
                    );
        }
    }


    // =========================================================
    // GET SECURE COMPLAINT
    // =========================================================

    @GetMapping("/complaint/{complaintId}")
    public ResponseEntity<?> getSecureComplaint(
            @PathVariable String complaintId,
            @RequestHeader(
                    value = "X-Secure-Access",
                    required = false
            ) String token) {

        Optional<Complaint> complaintOptional =
                complaintRepository.findByComplaintId(
                        complaintId.trim()
                );


        if (complaintOptional.isEmpty()) {

            return ResponseEntity.status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint not found."
                            )
                    );
        }


        Complaint complaint =
                complaintOptional.get();


        if (!isValidSecureAccess(
                token,
                complaintId,
                complaint.getVictimRegistrationNumber()
        )) {

            return ResponseEntity.status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "Secure access is invalid or expired."
                            )
                    );
        }


        return ResponseEntity.ok(
                complaint
        );
    }


    // =========================================================
    // UPDATE SECURE COMPLAINT
    // =========================================================

    @PutMapping("/complaint/{complaintId}")
    public ResponseEntity<?> updateSecureComplaint(
            @PathVariable String complaintId,
            @RequestHeader(
                    value = "X-Secure-Access",
                    required = false
            ) String token,
            @RequestBody ComplaintEditRequest request) {

        Optional<Complaint> complaintOptional =
                complaintRepository.findByComplaintId(
                        complaintId.trim()
                );


        if (complaintOptional.isEmpty()) {

            return ResponseEntity.status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint not found."
                            )
                    );
        }


        Complaint complaint =
                complaintOptional.get();


        if (!isValidSecureAccess(
                token,
                complaintId,
                complaint.getVictimRegistrationNumber()
        )) {

            return ResponseEntity.status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "Secure access is invalid or expired."
                            )
                    );
        }


        if (request == null) {

            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint data is required."
                            )
                    );
        }


        if (request.complaintType != null) {

            complaint.setComplaintType(
                    request.complaintType
            );
        }


        if (request.incidentDate != null) {

            complaint.setIncidentDate(
                    request.incidentDate
            );
        }


        if (request.incidentLocation != null) {

            complaint.setIncidentLocation(
                    request.incidentLocation
            );
        }


        if (request.subject != null) {

            complaint.setSubject(
                    request.subject
            );
        }


        if (request.description != null) {

            complaint.setDescription(
                    request.description
            );
        }


        if (request.suspectInformation != null) {

            complaint.setSuspectInformation(
                    request.suspectInformation
            );
        }


        if (request.financialLoss != null) {

            complaint.setFinancialLoss(
                    request.financialLoss
            );
        }


        // Edited complaint must be checked again
        complaint.setStatus(
                "PENDING"
        );


        complaint.setRejectionReason(
                null
        );


        Complaint saved =
                complaintRepository.save(
                        complaint
                );


        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Complaint updated successfully. It is now pending for verification.",
                        "complaint",
                        saved
                )
        );
    }


    // =========================================================
    // GET SECURE EVIDENCE
    // =========================================================

    @GetMapping("/complaint/{complaintId}/evidence")
    public ResponseEntity<?> getSecureEvidence(
            @PathVariable String complaintId,
            @RequestHeader(
                    value = "X-Secure-Access",
                    required = false
            ) String token) {

        Optional<Complaint> complaintOptional =
                complaintRepository.findByComplaintId(
                        complaintId.trim()
                );


        if (complaintOptional.isEmpty()) {

            return ResponseEntity.status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Complaint not found."
                            )
                    );
        }


        Complaint complaint =
                complaintOptional.get();


        if (!isValidSecureAccess(
                token,
                complaintId,
                complaint.getVictimRegistrationNumber()
        )) {

            return ResponseEntity.status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "Secure access is invalid or expired."
                            )
                    );
        }


        List<Evidence> evidenceList =
                evidenceRepository.findByComplaintId(
                        complaintId.trim()
                );


        List<Map<String, Object>> safeEvidence =
                new ArrayList<>();


        for (Evidence evidence : evidenceList) {

            Map<String, Object> item =
                    new HashMap<>();


            item.put(
                    "evidenceId",
                    evidence.getEvidenceId()
            );

            item.put(
                    "complaintId",
                    evidence.getComplaintId()
            );

            item.put(
                    "originalFileName",
                    evidence.getOriginalFileName()
            );

            item.put(
                    "fileType",
                    evidence.getFileType()
            );

            item.put(
                    "fileSize",
                    evidence.getFileSize()
            );

            item.put(
                    "verificationStatus",
                    evidence.getVerificationStatus()
            );

            item.put(
                    "encryptionStatus",
                    evidence.getEncryptionStatus()
            );

            item.put(
                    "uploadedAt",
                    evidence.getUploadedAt()
            );


            safeEvidence.add(
                    item
            );
        }


        return ResponseEntity.ok(
                safeEvidence
        );
    }


    // =========================================================
    // VALIDATE SECURE ACCESS
    // =========================================================

    private boolean isValidSecureAccess(
            String token,
            String complaintId,
            String registrationNumber) {

        if (isBlank(token) ||
                isBlank(complaintId) ||
                isBlank(registrationNumber)) {

            return false;
        }


        String cleanToken =
                token.trim();


        Optional<SecureAccessToken> tokenOptional =
                secureAccessTokenRepository
                        .findByToken(
                                cleanToken
                        );


        if (tokenOptional.isEmpty()) {

            return false;
        }


        SecureAccessToken accessToken =
                tokenOptional.get();


        if (!complaintId
                .trim()
                .equalsIgnoreCase(
                        accessToken.getComplaintId()
                )) {

            return false;
        }


        if (!registrationNumber
                .trim()
                .equalsIgnoreCase(
                        accessToken
                                .getVictimRegistrationNumber()
                )) {

            return false;
        }


        return secureAccessService
                .validateAccessToken(
                        cleanToken,
                        complaintId.trim(),
                        registrationNumber.trim()
                );
    }


    // =========================================================
    // BLANK CHECK
    // =========================================================

    private boolean isBlank(String value) {

        return value == null ||
                value.trim().isEmpty();
    }


    // =========================================================
    // OTP REQUEST
    // =========================================================

    public static class OtpRequest {

        public String complaintId;
        public String registrationNumber;
        public String email;
    }


    // =========================================================
    // VERIFY OTP REQUEST
    // =========================================================

    public static class VerifyOtpRequest {

        public String complaintId;
        public String registrationNumber;
        public String email;
        public String otp;
    }


    // =========================================================
    // COMPLAINT EDIT REQUEST
    // =========================================================

    public static class ComplaintEditRequest {

        public String complaintType;
        public String incidentDate;
        public String incidentLocation;
        public String subject;
        public String description;
        public String suspectInformation;
        public String financialLoss;
    }
}