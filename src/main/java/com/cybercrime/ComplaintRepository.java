package com.cybercrime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Optional<Complaint> findByComplaintId(String complaintId);

    List<Complaint> findByVictimRegistrationNumberOrderByCreatedAtDesc(
            String victimRegistrationNumber
    );

    long countByVictimRegistrationNumber(
            String victimRegistrationNumber
    );

    long countByVictimRegistrationNumberAndStatus(
            String victimRegistrationNumber,
            String status
    );
}