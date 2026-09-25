package com.cybercrime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/complaints")
@CrossOrigin
public class ComplaintController {

    private final ComplaintRepository complaintRepository;

    public ComplaintController(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    // =========================================================
    // REGISTER NEW COMPLAINT
    // =========================================================

    @PostMapping
    public ResponseEntity<?> createComplaint(
            @RequestBody ComplaintRequest request) {

        try {

            if (request.victimRegistrationNumber == null ||
                    request.victimRegistrationNumber.trim().isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Victim registration number is required")
                );
            }

            if (request.victimName == null ||
                    request.victimName.trim().isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Victim name is required")
                );
            }

            Complaint complaint = new Complaint();

            // Generate unique complaint ID.
            String complaintId =
                    "CMP-" +
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

            complaint.setComplaintId(complaintId);

            complaint.setVictimRegistrationNumber(
                    request.victimRegistrationNumber.trim()
            );

            complaint.setVictimName(
                    request.victimName.trim()
            );

            complaint.setComplaintType(
                    request.complaintType
            );

            complaint.setIncidentDate(
                    request.incidentDate
            );

            complaint.setIncidentLocation(
                    request.incidentLocation
            );

            complaint.setSubject(
                    request.subject
            );

            complaint.setDescription(
                    request.description
            );

            complaint.setSuspectInformation(
                    request.suspectInformation
            );

            complaint.setFinancialLoss(
                    request.financialLoss
            );

            complaint.setStatus("PENDING");

            Complaint saved =
                    complaintRepository.save(complaint);

            Map<String, Object> response =
                    new HashMap<>();

            response.put(
                    "message",
                    "Complaint registered successfully"
            );

            response.put(
                    "complaintId",
                    saved.getComplaintId()
            );

            response.put(
                    "status",
                    saved.getStatus()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to register complaint"
                    ));
        }
    }

    // =========================================================
    // GET ALL COMPLAINTS - ADMIN
    // =========================================================

    @GetMapping("/all")
    public ResponseEntity<?> getAllComplaints() {

        try {

            List<Complaint> complaints =
                    complaintRepository.findAll();

            return ResponseEntity.ok(complaints);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to load complaints"
                    ));
        }
    }

    // =========================================================
    // GET VICTIM COMPLAINTS
    // =========================================================

    @GetMapping("/user/{registrationNumber}")
    public ResponseEntity<?> getUserComplaints(
            @PathVariable String registrationNumber) {

        try {

            List<Complaint> complaints =
                    complaintRepository
                            .findByVictimRegistrationNumberOrderByCreatedAtDesc(
                                    registrationNumber
                            );

            /*
             * Important:
             * Sensitive complaint details are NOT returned here.
             *
             * My Complaints page only needs:
             * Complaint ID + Status + Date.
             *
             * Full details are loaded through secure OTP access.
             */

            List<Map<String, Object>> secureList =
                    complaints.stream()
                            .map(this::createSafeComplaintResponse)
                            .toList();

            return ResponseEntity.ok(secureList);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to load complaints"
                    ));
        }
    }

    // =========================================================
    // GET ONE COMPLAINT - ADMIN
    // =========================================================

    @GetMapping("/{complaintId}")
    public ResponseEntity<?> getComplaint(
            @PathVariable String complaintId) {

        Optional<Complaint> optional =
                complaintRepository
                        .findByComplaintId(complaintId);

        if (optional.isEmpty()) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Complaint not found"
                    ));
        }

        return ResponseEntity.ok(optional.get());
    }

    // =========================================================
    // EDIT COMPLAINT
    // =========================================================

    @PutMapping("/{complaintId}")
    public ResponseEntity<?> updateComplaint(
            @PathVariable String complaintId,
            @RequestBody ComplaintRequest request) {

        try {

            Optional<Complaint> optional =
                    complaintRepository
                            .findByComplaintId(complaintId);

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Complaint not found"
                        ));
            }

            Complaint complaint =
                    optional.get();

            /*
             * Victim should only edit their own complaint
             * after secure OTP verification on frontend.
             *
             * Backend ownership is checked using the
             * registration number supplied in the request.
             */

            if (request.victimRegistrationNumber != null &&
                    !request.victimRegistrationNumber.equals(
                            complaint.getVictimRegistrationNumber()
                    )) {

                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "Access denied"
                        ));
            }

            if (request.victimName != null) {
                complaint.setVictimName(
                        request.victimName.trim()
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

            /*
             * Whenever victim changes complaint information,
             * send it back to PENDING so admin can review
             * the updated information.
             */
            complaint.setStatus("PENDING");
            complaint.setRejectionReason(null);

            Complaint updated =
                    complaintRepository.save(complaint);

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "Complaint updated successfully",
                            "complaintId",
                            updated.getComplaintId(),
                            "status",
                            updated.getStatus()
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to update complaint"
                    ));
        }
    }

    // =========================================================
    // ADMIN CHANGE STATUS
    // =========================================================

    @PutMapping("/{complaintId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String complaintId,
            @RequestBody StatusRequest request) {

        try {

            Optional<Complaint> optional =
                    complaintRepository
                            .findByComplaintId(complaintId);

            if (optional.isEmpty()) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Complaint not found"
                        ));
            }

            Complaint complaint =
                    optional.get();

            String status =
                    request.status == null
                            ? ""
                            : request.status.trim().toUpperCase();

            if (!status.equals("PENDING") &&
                    !status.equals("APPROVED") &&
                    !status.equals("REJECTED")) {

                return ResponseEntity
                        .badRequest()
                        .body(Map.of(
                                "message",
                                "Invalid complaint status"
                        ));
            }

            complaint.setStatus(status);

            if (status.equals("REJECTED")) {

                complaint.setRejectionReason(
                        request.rejectionReason
                );

            } else {

                complaint.setRejectionReason(null);
            }

            Complaint updated =
                    complaintRepository.save(complaint);

            Map<String, Object> response =
                    new HashMap<>();

            response.put(
                    "message",
                    "Complaint status updated successfully"
            );

            response.put(
                    "complaintId",
                    updated.getComplaintId()
            );

            response.put(
                    "status",
                    updated.getStatus()
            );

            response.put(
                    "rejectionReason",
                    updated.getRejectionReason()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to update complaint status"
                    ));
        }
    }

    // =========================================================
    // VICTIM COMPLAINT COUNTS
    // =========================================================

    @GetMapping("/user/{registrationNumber}/counts")
    public ResponseEntity<?> getComplaintCounts(
            @PathVariable String registrationNumber) {

        try {

            long total =
                    complaintRepository
                            .countByVictimRegistrationNumber(
                                    registrationNumber
                            );

            long pending =
                    complaintRepository
                            .countByVictimRegistrationNumberAndStatus(
                                    registrationNumber,
                                    "PENDING"
                            );

            long approved =
                    complaintRepository
                            .countByVictimRegistrationNumberAndStatus(
                                    registrationNumber,
                                    "APPROVED"
                            );

            long rejected =
                    complaintRepository
                            .countByVictimRegistrationNumberAndStatus(
                                    registrationNumber,
                                    "REJECTED"
                            );

            Map<String, Long> response =
                    new HashMap<>();

            response.put("total", total);
            response.put("pending", pending);
            response.put("approved", approved);
            response.put("rejected", rejected);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to load complaint counts"
                    ));
        }
    }

    // =========================================================
    // SAFE RESPONSE FOR MY COMPLAINTS
    // =========================================================

    private Map<String, Object> createSafeComplaintResponse(
            Complaint complaint) {

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "complaintId",
                complaint.getComplaintId()
        );

        response.put(
                "status",
                complaint.getStatus()
        );

        response.put(
                "createdAt",
                complaint.getCreatedAt()
        );

        response.put(
                "updatedAt",
                complaint.getUpdatedAt()
        );

        response.put(
                "complaintType",
                complaint.getComplaintType()
        );

        return response;
    }

    // =========================================================
    // REQUEST CLASS
    // =========================================================

    public static class ComplaintRequest {

        public String victimRegistrationNumber;

        public String victimName;

        public String complaintType;

        public String incidentDate;

        public String incidentLocation;

        public String subject;

        public String description;

        public String suspectInformation;

        public String financialLoss;
    }

    // =========================================================
    // STATUS REQUEST
    // =========================================================

    public static class StatusRequest {

        public String status;

        public String rejectionReason;
    }
}