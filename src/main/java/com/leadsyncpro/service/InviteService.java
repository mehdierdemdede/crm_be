package com.leadsyncpro.service;

import com.leadsyncpro.model.InviteToken;
import com.leadsyncpro.model.User;
import com.leadsyncpro.repository.InviteTokenRepository;
import com.leadsyncpro.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class InviteService {
    private final InviteTokenRepository inviteTokenRepository;
    private final MailService mailService;
    private final long expireDays;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public InviteService(InviteTokenRepository inviteTokenRepository,
                         MailService mailService,
                         @Value("${app.invite.expire-days:7}") int expireDays) {
        this.inviteTokenRepository = inviteTokenRepository;
        this.mailService = mailService;
        this.expireDays = expireDays;
    }

    @Transactional
    public InviteToken createInvite(User user) {
        // önce varsa eski token'ı temizle
        inviteTokenRepository.deleteByUser(user);

        String token = generateToken();
        InviteToken it = new InviteToken();
        it.setToken(token);
        it.setUser(user);
        it.setExpiresAt(Instant.now().plus(expireDays, ChronoUnit.DAYS));
        inviteTokenRepository.save(it);

        mailService.sendInviteEmail(user.getEmail(), token, user.getFirstName());
        return it;
    }

    @Transactional
    public void acceptInvite(String token, String rawPassword, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        InviteToken it = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (it.getExpiresAt().isBefore(Instant.now())) {
            // token süresi dolmuş
            inviteTokenRepository.delete(it);
            throw new IllegalArgumentException("Invite token expired");
        }

        User user = it.getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        // tek kullanımlık: sil
        inviteTokenRepository.delete(it);
    }

    private String generateToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return urlEncoder.encodeToString(randomBytes);
    }
}

