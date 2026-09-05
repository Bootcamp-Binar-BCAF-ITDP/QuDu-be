package com.delvin.loan.repository;

import com.delvin.loan.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomer_CustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    long countByCustomer_CustomerIdAndReadFalse(String customerId);

    @Modifying
    @Query("update Notification n set n.read = true "
            + "where n.customer.customerId = :customerId and n.read = false")
    int markAllRead(@Param("customerId") String customerId);
}
