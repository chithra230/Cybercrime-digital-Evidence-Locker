package com.cybercrime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/secure-preview")
@CrossOrigin
public class SecureComplaintPreviewController {

    private final ComplaintRepository complaintRepository;
    private final EncryptionService encryptionService;

    public SecureComplaintPreviewController(
            ComplaintRepository complaintRepository,
            EncryptionService encryptionService) {

        this.complaintRepository = complaintRepository;
        this.encryptionService = encryptionService;
    }


    // =========================================================
    // ENCRYPTED COMPLAINT PREVIEW
    // =========================================================

    @GetMapping("/{complaintId}")
    public ResponseEntity<?> getEncryptedComplaintPreview(
            @PathVariable String complaintId,
            @RequestParam String registrationNumber) {

        try {

            Optional<Complaint> optional =
                    complaintRepository.findByComplaintId(
                            complaintId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Complaint not found"
                                )
                        );
            }


            Complaint complaint =
                    optional.get();


            // -------------------------------------------------
            // CHECK VICTIM OWNERSHIP
            // -------------------------------------------------

            if (!registrationNumber.equals(
                    complaint.getVictimRegistrationNumber()
            )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                Map.of(
                                        "message",
                                        "Access denied"
                                )
                        );
            }


            // -------------------------------------------------
            // CREATE TEMP PLAINTEXT
            // -------------------------------------------------

            String complaintData =

                    "Complaint ID: "
                    + safe(complaint.getComplaintId())
                    + "\n\n"

                    + "Complaint Type: "
                    + safe(complaint.getComplaintType())
                    + "\n\n"

                    + "Incident Date: "
                    + safe(complaint.getIncidentDate())
                    + "\n\n"

                    + "Incident Location: "
                    + safe(complaint.getIncidentLocation())
                    + "\n\n"

                    + "Subject: "
                    + safe(complaint.getSubject())
                    + "\n\n"

                    + "Description: "
                    + safe(complaint.getDescription())
                    + "\n\n"

                    + "Suspect Information: "
                    + safe(complaint.getSuspectInformation())
                    + "\n\n"

                    + "Financial Loss: "
                    + safe(complaint.getFinancialLoss())
                    + "\n\n"

                    + "Status: "
                    + safe(complaint.getStatus());


            // -------------------------------------------------
            // TEMP FILES
            // -------------------------------------------------

            Path tempPlain =
                    Files.createTempFile(
                            "complaint-preview-",
                            ".txt"
                    );

            Path tempEncrypted =
                    Files.createTempFile(
                            "complaint-preview-encrypted-",
                            ".enc"
                    );


            try {

                Files.writeString(
                        tempPlain,
                        complaintData
                );


                // -------------------------------------------------
                // AES-256-GCM USING EXISTING ENCRYPTION SERVICE
                // -------------------------------------------------

                encryptionService.encryptFile(
                        tempPlain,
                        tempEncrypted
                );


                byte[] encryptedBytes =
                        Files.readAllBytes(
                                tempEncrypted
                        );


                String cipherText =
                        Base64.getEncoder()
                                .encodeToString(
                                        encryptedBytes
                                );


                Map<String, Object> result =
                        new LinkedHashMap<>();


                result.put(
                        "complaintId",
                        complaint.getComplaintId()
                );

                result.put(
                        "algorithm",
                        "AES-256-GCM"
                );

                result.put(
                        "cipherText",
                        cipherText
                );


                return ResponseEntity.ok(
                        result
                );


            } finally {

                Files.deleteIfExists(
                        tempPlain
                );

                Files.deleteIfExists(
                        tempEncrypted
                );
            }


        } catch (Exception e) {

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to generate encrypted complaint preview",
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }


    private String safe(String value) {

        return value == null
                ? ""
                : value;
    }
}