package com.delvin.loan.repository;

import com.delvin.loan.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    boolean existsByEmail(String email);

    boolean existsByNik(String nik);

    Optional<Customer> findByEmail(String email);

}
