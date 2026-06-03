package com.filer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${filer.notify-email-enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Async
    public void sendJobCompleted(String toEmail, String jobId, String downloadPath) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) return;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("Filer: Your conversion is ready");
        msg.setText("Job " + jobId + " has completed.\n\nDownload: " + downloadPath);
        mailSender.send(msg);
    }

    @Async
    public void sendJobFailed(String toEmail, String jobId, String reason) {
        if (!emailEnabled || toEmail == null || toEmail.isBlank()) return;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(toEmail);
        msg.setSubject("Filer: Conversion failed");
        msg.setText("Job " + jobId + " failed.\nReason: " + reason);
        mailSender.send(msg);
    }
}
