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

    enum Outcome { SENT, SMTP_NOT_CONFIGURED, FAILED }

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                 org.springframework.core.env.Environment env) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = env.getProperty("executionos.mail.from", "no-reply@skillforge.example");
    }

    boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null;
    }

    /**
     * Returns what actually happened instead of a bare void. Callers that
     * need to report a truthful "N emails sent" count (invitation batches,
     * the settings-page test-send action) previously had no way to tell a
     * real send from a silently-swallowed failure or from SMTP not being
     * configured at all -- every call looked identical from the outside.
     */
    Outcome send(String to, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.info("SMTP not configured (SMTP_HOST unset) — would have sent email to {}: [{}]\n{}", to, subject, body);
            return Outcome.SMTP_NOT_CONFIGURED;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return Outcome.SENT;
        } catch (Exception ex) {
            // Never let an email delivery failure break the calling request
            // (e.g. a user creation or password reset should still succeed) --
            // but the caller now gets told it failed, rather than nothing.
            log.warn("Failed to send email to {}: {}", mask(to), ex.getMessage());
            return Outcome.FAILED;
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.charAt(0) + "***" + email.substring(at);
    }
}
