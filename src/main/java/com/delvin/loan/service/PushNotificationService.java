package com.delvin.loan.service;

import com.delvin.loan.model.DeviceToken;
import com.delvin.loan.repository.DeviceTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;

    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;

    @Transactional
    public void sendToCustomer(String customerId,
                               String title,
                               String body,
                               Map<String, String> data) {

        List<DeviceToken> devices = deviceTokenRepository.findByCustomer_CustomerId(customerId);

        if (devices.isEmpty()) {
            log.debug("No device token registered for customer {} - push skipped", customerId);
            return;
        }

        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();

        if (messaging == null) {
            log.info("Firebase disabled - would have pushed \"{}\" to {} device(s) of customer {}",
                    title, devices.size(), customerId);
            return;
        }

        List<String> tokens = devices.stream().map(DeviceToken::getToken).toList();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data == null ? Map.of() : data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("loan_status")
                                .build())
                        .build())
                .build();

        try {
            BatchResponse response = messaging.sendEachForMulticast(message);

            log.info("Push for customer {}: {} delivered, {} failed",
                    customerId, response.getSuccessCount(), response.getFailureCount());

            if (response.getFailureCount() > 0) {
                pruneDeadTokens(tokens, response.getResponses());
            }
        } catch (FirebaseMessagingException e) {
            log.error("Could not push to customer {}", customerId, e);
        }
    }

    private void pruneDeadTokens(List<String> tokens, List<SendResponse> responses) {

        List<String> dead = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {

            SendResponse result = responses.get(i);
            if (result.isSuccessful()) continue;

            FirebaseMessagingException error = result.getException();
            if (error == null) continue;

            MessagingErrorCode code = error.getMessagingErrorCode();

            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                dead.add(tokens.get(i));
            } else {
                log.warn("Push to token index {} failed with {}", i, code);
            }
        }

        if (!dead.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(dead);
            log.info("Removed {} dead device token(s)", dead.size());
        }
    }
}
