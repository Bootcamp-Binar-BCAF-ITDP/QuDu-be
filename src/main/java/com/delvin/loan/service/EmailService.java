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

    public void sendResetPasswordCodeEmail(
            String email,
            String code,
            int validMinutes
    ) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("Kode Reset Password - Quick Duit");

        message.setText("""
                Halo,

                Kami menerima permintaan untuk mengatur ulang kata sandi Anda.

                Kode verifikasi Anda:

                    %s

                Masukkan kode ini di aplikasi QuickDuit. Kode berlaku %d menit
                dan hanya bisa dipakai satu kali.

                Jika Anda tidak meminta pengaturan ulang kata sandi, abaikan
                email ini - kata sandi Anda tidak berubah.

                Jangan bagikan kode ini kepada siapa pun, termasuk yang mengaku
                sebagai petugas QuickDuit.
                """.formatted(code, validMinutes));

        mailSender.send(message);
    }
}