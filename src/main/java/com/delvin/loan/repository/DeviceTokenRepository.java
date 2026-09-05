package com.delvin.loan.repository;

import com.delvin.loan.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByCustomer_CustomerId(String customerId);

    void deleteByToken(String token);

    void deleteByTokenIn(List<String> tokens);
}
