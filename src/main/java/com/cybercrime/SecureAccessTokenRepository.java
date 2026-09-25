package com.cybercrime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface SecureAccessTokenRepository
        extends JpaRepository<SecureAccessToken, Long> {

    Optional<SecureAccessToken> findByToken(String token);

    @Transactional
    void deleteByComplaintIdAndVictimRegistrationNumber(
            String complaintId,
            String victimRegistrationNumber
    );
}