package com.executionos.skillforge.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email (password resets, invites) when SMTP is
 * configured, and safely logs the content instead when it isn't — this
 * never crashes the app either way. spring-boot-starter-mail only creates a
 * JavaMailSender bean when spring.mail.host is set, so this depends on
 * ObjectProvider (never fails to construct) rather than JavaMailSender
 * directly (which would fail startup with "no qualifying bean" the moment
 * SMTP isn't configured — the same class of bug as the earlier oauth2Login
 * crash, just for a different dependency).
 */
@Service
class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                 org.springframework.core.env.Environment env) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = env.getProperty("executionos.mail.from", "no-reply@skillforge.example");
    }

    void send(String to, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.info("SMTP not configured (SMTP_HOST unset) — would have sent email to {}: [{}]\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception ex) {
            // Never let an email delivery failure break the calling request
            // (e.g. a user creation or password reset should still succeed).
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
