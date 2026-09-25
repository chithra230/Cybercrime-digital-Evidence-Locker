package com.cybercrime;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/evidence")
@CrossOrigin
public class EvidenceController {

    private final EvidenceRepository evidenceRepository;
    private final ComplaintRepository complaintRepository;
    private final EncryptionService encryptionService;
    private final SecureAccessService secureAccessService;
    private final SecureAccessTokenRepository secureAccessTokenRepository;

    private final Path uploadDirectory =
            Paths.get(
                    System.getProperty("user.dir"),
                    "evidence-storage"
            )
            .toAbsolutePath()
            .normalize();

    private final Path encryptedDirectory =
            uploadDirectory.resolve("encrypted");

    public EvidenceController(
            EvidenceRepository evidenceRepository,
            ComplaintRepository complaintRepository,
            EncryptionService encryptionService,
            SecureAccessService secureAccessService,
            SecureAccessTokenRepository secureAccessTokenRepository) {

        this.evidenceRepository = evidenceRepository;
        this.complaintRepository = complaintRepository;
        this.encryptionService = encryptionService;
        this.secureAccessService = secureAccessService;
        this.secureAccessTokenRepository = secureAccessTokenRepository;

        try {
            Files.createDirectories(uploadDirectory);
            Files.createDirectories(encryptedDirectory);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create evidence storage",
                    e
            );
        }
    }

    // =========================================================
    // UPLOAD EVIDENCE
    // =========================================================

    @PostMapping("/upload")
    public ResponseEntity<?> uploadEvidence(
            @RequestParam("file") MultipartFile file,
            @RequestParam("complaintId") String complaintId,
            @RequestParam("victimRegistrationNumber")
            String victimRegistrationNumber) {

        try {

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "message",
                                "Please select an evidence file"
                        )
                );
            }

            if (complaintId == null ||
                    complaintId.trim().isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "message",
                                "Complaint ID is required"
                        )
                );
            }

            if (victimRegistrationNumber == null ||
                    victimRegistrationNumber.trim().isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "message",
                                "Victim registration number is required"
                        )
                );
            }

            Optional<Complaint> complaintOptional =
                    complaintRepository.findByComplaintId(
                            complaintId.trim()
                    );

            if (complaintOptional.isEmpty()) {

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
                    complaintOptional.get();

            if (!victimRegistrationNumber.trim().equals(
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

            String evidenceId;

            do {

                evidenceId =
                        "EVD-" +
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase();

            } while (
                    evidenceRepository
                            .findByEvidenceId(evidenceId)
                            .isPresent()
            );

            String originalFileName =
                    file.getOriginalFilename();

            if (originalFileName == null ||
                    originalFileName.trim().isEmpty()) {

                originalFileName = "evidence-file";
            }

            String safeFileName =
                    Paths.get(originalFileName)
                            .getFileName()
                            .toString();

            String storedFileName =
                    evidenceId +
                    "_" +
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                    +
                    "_" +
                    safeFileName;

            Path pendingFile =
                    uploadDirectory
                            .resolve(storedFileName)
                            .normalize();

            Files.write(
                    pendingFile,
                    file.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            String sha256 =
                    calculateSha256(file.getBytes());

            Evidence evidence =
                    new Evidence();

            evidence.setEvidenceId(evidenceId);

            evidence.setComplaintId(
                    complaintId.trim()
            );

            evidence.setVictimRegistrationNumber(
                    victimRegistrationNumber.trim()
            );

            evidence.setOriginalFileName(
                    safeFileName
            );

            evidence.setFileType(
                    file.getContentType()
            );

            evidence.setFileSize(
                    file.getSize()
            );

            evidence.setFilePath(
                    pendingFile.toString()
            );

            evidence.setSha256Hash(
                    sha256
            );

            evidence.setEncryptionStatus(
                    "PENDING"
            );

            evidence.setUploadedAt(
                    LocalDateTime.now()
            );

            evidence.setVerificationStatus(
                    "PENDING"
            );

            Evidence saved =
                    evidenceRepository.save(evidence);

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Evidence uploaded successfully",

                            "evidenceId",
                            saved.getEvidenceId(),

                            "complaintId",
                            saved.getComplaintId(),

                            "fileName",
                            saved.getOriginalFileName(),

                            "verificationStatus",
                            saved.getVerificationStatus()
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to upload evidence",
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // GET EVIDENCE FOR COMPLAINT
    // =========================================================

    @GetMapping("/complaint/{complaintId}")
    public ResponseEntity<?> getEvidenceByComplaint(
            @PathVariable String complaintId) {

        try {

            List<Evidence> evidenceList =
                    evidenceRepository.findByComplaintId(
                            complaintId
                    );

            return ResponseEntity.ok(evidenceList);

        } catch (Exception e) {

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to load evidence"
                            )
                    );
        }
    }

    // =========================================================
    // GET ONE EVIDENCE
    // =========================================================

    @GetMapping("/{evidenceId}")
    public ResponseEntity<?> getEvidence(
            @PathVariable String evidenceId) {

        Optional<Evidence> optional =
                evidenceRepository.findByEvidenceId(
                        evidenceId
                );

        if (optional.isEmpty()) {

            return ResponseEntity
                    .status(404)
                    .body(
                            Map.of(
                                    "message",
                                    "Evidence not found"
                            )
                    );
        }

        return ResponseEntity.ok(
                optional.get()
        );
    }

    // =========================================================
    // ADMIN APPROVE / REJECT / PENDING
    // =========================================================

    @PutMapping("/{evidenceId}/status")
    public ResponseEntity<?> updateEvidenceStatus(
            @PathVariable String evidenceId,
            @RequestBody StatusRequest request) {

        try {

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            String status =
                    request.status == null
                            ? ""
                            : request.status
                                    .trim()
                                    .toUpperCase();

            if (!status.equals("APPROVED") &&
                    !status.equals("REJECTED") &&
                    !status.equals("PENDING")) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "message",
                                        "Invalid evidence status"
                                )
                        );
            }

            if (status.equals("APPROVED")) {
                encryptApprovedEvidence(evidence);
            }

            if (status.equals("REJECTED")) {

                evidence.setVerificationStatus(
                        "REJECTED"
                );
            }

            if (status.equals("PENDING")) {

                evidence.setVerificationStatus(
                        "PENDING"
                );
            }

            Evidence saved =
                    evidenceRepository.save(evidence);

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Evidence status updated successfully",

                            "evidenceId",
                            saved.getEvidenceId(),

                            "status",
                            saved.getVerificationStatus(),

                            "encryptionStatus",
                            saved.getEncryptionStatus(),

                            "rejectionReason",
                            request.rejectionReason == null
                                    ? ""
                                    : request.rejectionReason
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to update evidence",
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // ENCRYPT APPROVED EVIDENCE
    // =========================================================

    private void encryptApprovedEvidence(
            Evidence evidence) throws Exception {

        Path currentFile =
                Paths.get(
                        evidence.getFilePath()
                );

        if (!Files.exists(currentFile)) {

            throw new IOException(
                    "Evidence file not found"
            );
        }

        if ("ENCRYPTED".equalsIgnoreCase(
                evidence.getEncryptionStatus()
        )) {

            evidence.setVerificationStatus(
                    "APPROVED"
            );

            return;
        }

        String encryptedFileName =
                evidence.getEvidenceId() +
                ".enc";

        Path encryptedFile =
                encryptedDirectory
                        .resolve(encryptedFileName)
                        .normalize();

        encryptionService.encryptFile(
                currentFile,
                encryptedFile
        );

        Files.deleteIfExists(
                currentFile
        );

        evidence.setFilePath(
                encryptedFile.toString()
        );

        evidence.setEncryptionStatus(
                "ENCRYPTED"
        );

        evidence.setVerificationStatus(
                "APPROVED"
        );
    }

    // =========================================================
    // ACTUAL ENCRYPTED PREVIEW
    // =========================================================

    @GetMapping("/encrypted-preview/{evidenceId}")
    public ResponseEntity<?> encryptedPreview(
            @PathVariable String evidenceId) {

        try {

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            if (!"ENCRYPTED".equalsIgnoreCase(
                    evidence.getEncryptionStatus()
            )) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence is not encrypted yet"
                                )
                        );
            }

            Path encryptedFile =
                    Paths.get(
                            evidence.getFilePath()
                    );

            if (!Files.exists(encryptedFile)) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Encrypted evidence file not found"
                                )
                        );
            }

            byte[] encryptedBytes =
                    Files.readAllBytes(
                            encryptedFile
                    );

            String encryptedBase64 =
                    Base64.getEncoder()
                            .encodeToString(
                                    encryptedBytes
                            );

            return ResponseEntity.ok(
                    Map.of(
                            "evidenceId",
                            evidence.getEvidenceId(),

                            "fileName",
                            evidence.getOriginalFileName(),

                            "fileType",
                            evidence.getFileType() == null
                                    ? "application/octet-stream"
                                    : evidence.getFileType(),

                            "fileSize",
                            evidence.getFileSize(),

                            "sha256Hash",
                            evidence.getSha256Hash(),

                            "encryptionStatus",
                            evidence.getEncryptionStatus(),

                            "verificationStatus",
                            evidence.getVerificationStatus(),

                            "encryptedData",
                            encryptedBase64
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to load encrypted preview"
                            )
                    );
        }
    }

    // =========================================================
    // NORMAL ADMIN FILE VIEW
    // =========================================================

    @GetMapping("/file/{evidenceId}")
    public ResponseEntity<?> viewEvidenceFile(
            @PathVariable String evidenceId) {

        try {

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            Path filePath =
                    Paths.get(
                            evidence.getFilePath()
                    );

            byte[] fileBytes;

            if ("ENCRYPTED".equalsIgnoreCase(
                    evidence.getEncryptionStatus()
            )) {

                fileBytes =
                        encryptionService.decryptFile(
                                filePath
                        );

            } else {

                if (!Files.exists(filePath)) {

                    return ResponseEntity
                            .status(404)
                            .body(
                                    Map.of(
                                            "message",
                                            "Evidence file not found"
                                    )
                            );
                }

                fileBytes =
                        Files.readAllBytes(
                                filePath
                        );
            }

            MediaType mediaType =
                    detectMediaType(
                            evidence.getFileType()
                    );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition
                                    .inline()
                                    .filename(
                                            evidence
                                                    .getOriginalFileName()
                                    )
                                    .build()
                                    .toString()
                    )
                    .body(
                            new ByteArrayResource(
                                    fileBytes
                            )
                    );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to open evidence file"
                            )
                    );
        }
    }

    // =========================================================
    // SECURE VICTIM FILE VIEW
    // =========================================================

    @GetMapping("/secure-file/{evidenceId}")
    public ResponseEntity<?> secureEvidenceFile(
            @PathVariable String evidenceId,
            @RequestHeader(value = "X-Secure-Access",
                    required = false) String token) {

        try {

            if (token == null ||
                    token.trim().isEmpty()) {

                return ResponseEntity
                        .status(401)
                        .body(
                                Map.of(
                                        "message",
                                        "Secure access token is required"
                                )
                        );
            }

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            Optional<SecureAccessToken> tokenOptional =
                    secureAccessTokenRepository.findByToken(
                            token.trim()
                    );

            if (tokenOptional.isEmpty()) {

                return ResponseEntity
                        .status(401)
                        .body(
                                Map.of(
                                        "message",
                                        "Invalid secure access token"
                                )
                        );
            }

            SecureAccessToken accessToken =
                    tokenOptional.get();

            String complaintId =
                    evidence.getComplaintId();

            String registrationNumber =
                    accessToken.getVictimRegistrationNumber();

            boolean valid =
                    secureAccessService.validateAccessToken(
                            token.trim(),
                            complaintId,
                            registrationNumber
                    );

            if (!valid) {

                return ResponseEntity
                        .status(401)
                        .body(
                                Map.of(
                                        "message",
                                        "Secure access validation failed"
                                )
                        );
            }

            if (!complaintId.equals(
                    accessToken.getComplaintId()
            )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence does not belong to this complaint"
                                )
                        );
            }

            if (!registrationNumber.equals(
                    evidence.getVictimRegistrationNumber()
            )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence ownership validation failed"
                                )
                        );
            }

            if (!"APPROVED".equalsIgnoreCase(
                    evidence.getVerificationStatus()
            )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence is not approved"
                                )
                        );
            }

            if (!"ENCRYPTED".equalsIgnoreCase(
                    evidence.getEncryptionStatus()
            )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence is not encrypted"
                                )
                        );
            }

            Path encryptedFile =
                    Paths.get(
                            evidence.getFilePath()
                    );

            if (!Files.exists(encryptedFile)) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Encrypted evidence file not found"
                                )
                        );
            }

            byte[] decryptedBytes =
                    encryptionService.decryptFile(
                            encryptedFile
                    );

            MediaType mediaType =
                    detectMediaType(
                            evidence.getFileType()
                    );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition
                                    .inline()
                                    .filename(
                                            evidence
                                                    .getOriginalFileName()
                                    )
                                    .build()
                                    .toString()
                    )
                    .body(
                            new ByteArrayResource(
                                    decryptedBytes
                            )
                    );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to decrypt evidence"
                            )
                    );
        }
    }

    // =========================================================
    // DOWNLOAD EVIDENCE
    // =========================================================

    @GetMapping("/download/{evidenceId}")
    public ResponseEntity<?> downloadEvidence(
            @PathVariable String evidenceId) {

        try {

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            Path filePath =
                    Paths.get(
                            evidence.getFilePath()
                    );

            byte[] fileBytes;

            if ("ENCRYPTED".equalsIgnoreCase(
                    evidence.getEncryptionStatus()
            )) {

                fileBytes =
                        encryptionService.decryptFile(
                                filePath
                        );

            } else {

                if (!Files.exists(filePath)) {

                    return ResponseEntity
                            .status(404)
                            .body(
                                    Map.of(
                                            "message",
                                            "Evidence file not found"
                                    )
                            );
                }

                fileBytes =
                        Files.readAllBytes(
                                filePath
                        );
            }

            MediaType mediaType =
                    detectMediaType(
                            evidence.getFileType()
                    );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition
                                    .attachment()
                                    .filename(
                                            evidence
                                                    .getOriginalFileName()
                                    )
                                    .build()
                                    .toString()
                    )
                    .body(
                            new ByteArrayResource(
                                    fileBytes
                            )
                    );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to download evidence"
                            )
                    );
        }
    }

    // =========================================================
    // DELETE EVIDENCE
    // =========================================================

    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<?> deleteEvidence(
            @PathVariable String evidenceId) {

        try {

            Optional<Evidence> optional =
                    evidenceRepository.findByEvidenceId(
                            evidenceId
                    );

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(404)
                        .body(
                                Map.of(
                                        "message",
                                        "Evidence not found"
                                )
                        );
            }

            Evidence evidence =
                    optional.get();

            Path filePath =
                    Paths.get(
                            evidence.getFilePath()
                    );

            Files.deleteIfExists(
                    filePath
            );

            evidenceRepository.delete(
                    evidence
            );

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Evidence deleted successfully"
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "message",
                                    "Unable to delete evidence"
                            )
                    );
        }
    }

    // =========================================================
    // SHA-256
    // =========================================================

    private String calculateSha256(
            byte[] data) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        byte[] hash =
                digest.digest(data);

        StringBuilder result =
                new StringBuilder();

        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }

        return result.toString();
    }

    // =========================================================
    // MEDIA TYPE
    // =========================================================

    private MediaType detectMediaType(
            String contentType) {

        if (contentType == null ||
                contentType.trim().isEmpty()) {

            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {

            return MediaType.parseMediaType(
                    contentType
            );

        } catch (Exception e) {

            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // =========================================================
    // STATUS REQUEST
    // =========================================================

    public static class StatusRequest {

        public String status;

        public String rejectionReason;
    }
}