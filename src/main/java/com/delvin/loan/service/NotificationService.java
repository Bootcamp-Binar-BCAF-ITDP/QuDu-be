package com.delvin.loan.service;

import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.response.notification.NotificationResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.Notification;
import com.delvin.loan.repository.CustomerRepository;
import com.delvin.loan.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CustomerRepository customerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String customerId, String type, String title, String body, String referenceId) {

        if (customerId == null) {
            log.warn("Notification of type {} has no customer - not stored", type);
            return;
        }

        Customer customer = customerRepository.findById(customerId).orElse(null);

        if (customer == null) {
            log.warn("Notification of type {} references unknown customer {}", type, customerId);
            return;
        }

        Notification notification = new Notification();
        notification.setCustomer(customer);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String customerId, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId, pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String customerId) {
        return notificationRepository.countByCustomer_CustomerIdAndReadFalse(customerId);
    }

    @Transactional
    public NotificationResponse markRead(String customerId, Long notificationId) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> BusinessException.notFound("Notification not found: " + notificationId));

        if (notification.getCustomer() == null
                || !notification.getCustomer().getCustomerId().equals(customerId)) {
            throw BusinessException.forbidden("This notification belongs to another customer");
        }

        notification.setRead(true);

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(String customerId) {
        return notificationRepository.markAllRead(customerId);
    }
}
