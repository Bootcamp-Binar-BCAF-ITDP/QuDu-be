package com.delvin.loan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendResetPasswordEmail(
            String email,
            String token
    ) {

        String resetLink = resetPasswordUrl.replace("{token}", token);

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Reset Password - Quick Duit");

        message.setText("""
                Hello,

                We received a request to reset your password.

                Click the link below to reset your password:

                %s

                This link will expire in 15 minutes.

                If you did not request a password reset,
                please ignore this email.
                """.formatted(resetLink));

        mailSender.send(message);
    }

    /** The code a new customer types back to prove the address is theirs. */
    public void sendRegistrationOtpEmail(
            String email,
            String code,
            int validMinutes
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Registration Verification Code - Quick Duit");

        message.setText("""
                Hello,

                Thank you for signing up with QuickDuit.

                Your verification code:

                    %s

                Enter this code in the app to finish creating your account.
                It is valid for %d minutes and can be used once.

                If you did not sign up, ignore this email - no account is
                created without this code.

                Never share this code with anyone, including people claiming
                to be QuickDuit staff.
                """.formatted(code, validMinutes));

        mailSender.send(message);
    }

    public void sendResetPasswordCodeEmail(
            String email,
            String code,
            int validMinutes
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Password Reset Code - Quick Duit");

        message.setText("""
                Hello,

                We received a request to reset your password.

                Your verification code:

                    %s

                Enter this code in the QuickDuit app. It is valid for %d
                minutes and can be used once.

                If you did not ask for a password reset, ignore this email -
                your password has not changed.

                Never share this code with anyone, including people claiming
                to be QuickDuit staff.
                """.formatted(code, validMinutes));

        mailSender.send(message);
    }
}