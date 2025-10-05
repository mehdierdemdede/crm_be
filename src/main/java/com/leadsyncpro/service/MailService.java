package com.leadsyncpro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Value("${app.mail.from:no-reply@crmpro.com}")
    private String from;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBase;

    private final JavaMailSender mailSender;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Retryable(value = MailException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendInviteEmail(String toEmail, String token, String firstName) {
        String link = frontendBase + "/invite-accept?token=" + token;

        String subject = "CRM Pro - Davetiniz var";
        String body = String.format("""
            Merhaba %s,

            Seni organizasyonumuza davet ettik. Hesabını aktif etmek ve şifre belirlemek için lütfen aşağıdaki linke tıkla:

            %s

            İyi çalışmalar,
            CRM Pro
            """, firstName == null ? "" : firstName, link);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);

        mailSender.send(msg);
    }

    @Retryable(value = MailException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendLeadAssignedEmail(String toEmail, String firstName, String leadName,
                                      String campaignName, String language, String status, String leadId) {

        String link = frontendBase + "/leads/" + leadId;

        String subject = "Yeni Lead Atandı: " + leadName;

        String body = String.format("""
        Merhaba %s,

        Size yeni bir lead atandı:

        Ad: %s
        Kampanya: %s
        Dil: %s
        Durum: %s

        Lead detaylarına buradan ulaşabilirsiniz:
        %s

        Başarılar,
        CRM Pro
        """,
                firstName == null ? "" : firstName,
                leadName,
                campaignName != null ? campaignName : "-",
                language != null ? language : "-",
                status != null ? status : "-",
                link
        );

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);

        mailSender.send(msg);
    }

}

