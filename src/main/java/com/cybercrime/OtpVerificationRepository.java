package com.cybercrime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification>
    findTopByComplaintIdAndEmailAndUsedFalseOrderByCreatedAtDesc(
            String complaintId,
            String email
    );

    @Transactional
    void deleteByComplaintIdAndEmail(
            String complaintId,
            String email
    );
}