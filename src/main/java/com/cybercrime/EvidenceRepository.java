package com.cybercrime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    Optional<Evidence> findByEvidenceId(String evidenceId);

    List<Evidence> findByComplaintId(String complaintId);

    List<Evidence> findByVictimRegistrationNumber(
            String victimRegistrationNumber
    );
}