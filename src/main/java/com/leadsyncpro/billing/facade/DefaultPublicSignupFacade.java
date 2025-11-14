package com.leadsyncpro.billing.facade;

import com.leadsyncpro.billing.service.PublicSignupException;
import com.leadsyncpro.model.Organization;
import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.SupportedLanguages;
import com.leadsyncpro.model.User;
import com.leadsyncpro.model.InviteToken;
import com.leadsyncpro.model.billing.BillingPeriod;
import com.leadsyncpro.model.billing.Plan;
import com.leadsyncpro.model.billing.Price;
import com.leadsyncpro.model.billing.PublicSignup;
import com.leadsyncpro.model.billing.PublicSignupStatus;
import com.leadsyncpro.repository.OrganizationRepository;
import com.leadsyncpro.repository.UserRepository;
import com.leadsyncpro.repository.billing.PlanRepository;
import com.leadsyncpro.repository.billing.PriceRepository;
import com.leadsyncpro.repository.billing.PublicSignupRepository;
import com.leadsyncpro.service.InviteService;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DefaultPublicSignupFacade implements PublicSignupFacade {

    private final PlanRepository planRepository;
    private final PriceRepository priceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PublicSignupRepository publicSignupRepository;
    private final InviteService inviteService;

    public DefaultPublicSignupFacade(
            PlanRepository planRepository,
            PriceRepository priceRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            PublicSignupRepository publicSignupRepository,
            InviteService inviteService) {
        this.planRepository = Objects.requireNonNull(planRepository, "planRepository must not be null");
        this.priceRepository = Objects.requireNonNull(priceRepository, "priceRepository must not be null");
        this.organizationRepository =
                Objects.requireNonNull(organizationRepository, "organizationRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.publicSignupRepository =
                Objects.requireNonNull(publicSignupRepository, "publicSignupRepository must not be null");
        this.inviteService = Objects.requireNonNull(inviteService, "inviteService must not be null");
    }

    @Override
    @Transactional
    public PublicSignupResult createSignup(CreatePublicSignupCmd command) {
        validateCommand(command);

        Plan plan = planRepository
                .findById(command.planId())
                .orElseThrow(() ->
                        new PublicSignupException(HttpStatus.NOT_FOUND, "Plan %s not found".formatted(command.planId())));

        Price price = priceRepository
                .findByPlanAndBillingPeriod(plan, command.billingPeriod())
                .orElseThrow(() -> new PublicSignupException(
                        HttpStatus.BAD_REQUEST,
                        "Plan %s does not support %s billing".formatted(plan.getCode(), command.billingPeriod())));

        validateSeatCount(command.seatCount(), price);

        Organization organization = createOrganization(command.organizationName());
        User admin = createAdminUser(command, organization);

        var inviteToken = createInviteToken(admin);

        PublicSignup signup = new PublicSignup();
        signup.setPlan(plan);
        signup.setPrice(price);
        signup.setBillingPeriod(command.billingPeriod());
        signup.setSeatCount(command.seatCount());
        signup.setOrganizationName(organization.getName());
        signup.setAdminFirstName(command.adminFirstName());
        signup.setAdminLastName(command.adminLastName());
        signup.setAdminEmail(admin.getEmail());
        signup.setAdminPhone(command.adminPhone());
        signup.setStatus(PublicSignupStatus.INVITE_SENT);
        signup.setStatusMessage("Invitation email sent to organization admin");
        signup.setOrganization(organization);
        signup.setAdminUser(admin);
        signup.setInviteToken(inviteToken.getToken());

        PublicSignup persisted = publicSignupRepository.save(signup);

        return new PublicSignupResult(
                persisted.getId(),
                organization.getId(),
                admin.getEmail(),
                inviteToken.getToken(),
                persisted.getStatus(),
                persisted.getStatusMessage());
    }

    private void validateCommand(CreatePublicSignupCmd command) {
        if (command == null) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "Signup payload is required");
        }
        if (command.planId() == null) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "planId is required");
        }
        BillingPeriod billingPeriod = command.billingPeriod();
        if (billingPeriod == null) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "billingPeriod is required");
        }
        if (command.seatCount() < 1 || command.seatCount() > 200) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "seatCount must be between 1 and 200");
        }
        if (!StringUtils.hasText(command.organizationName())) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "organizationName is required");
        }
        if (!StringUtils.hasText(command.adminFirstName())) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "admin.firstName is required");
        }
        if (!StringUtils.hasText(command.adminLastName())) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "admin.lastName is required");
        }
        if (!StringUtils.hasText(command.adminEmail())) {
            throw new PublicSignupException(HttpStatus.BAD_REQUEST, "admin.email is required");
        }
    }

    private void validateSeatCount(int seatCount, Price price) {
        Integer seatLimit = price.getSeatLimit();
        if (seatLimit != null && seatCount > seatLimit) {
            throw new PublicSignupException(
                    HttpStatus.BAD_REQUEST,
                    "Seat count %d exceeds the limit of %d for the selected plan".formatted(seatCount, seatLimit));
        }
    }

    private Organization createOrganization(String rawName) {
        String name = rawName.trim();
        Organization organization = new Organization();
        organization.setName(name);
        try {
            return organizationRepository.save(organization);
        } catch (DataIntegrityViolationException ex) {
            throw new PublicSignupException(HttpStatus.CONFLICT, "Organization name is already in use");
        }
    }

    private User createAdminUser(CreatePublicSignupCmd command, Organization organization) {
        User admin = new User();
        admin.setOrganizationId(organization.getId());
        admin.setEmail(command.adminEmail().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(null);
        admin.setFirstName(command.adminFirstName());
        admin.setLastName(command.adminLastName());
        admin.setRole(Role.SUPER_ADMIN);
        admin.setActive(true);
        admin.setAutoAssignEnabled(false);
        admin.setSupportedLanguages(EnumSet.noneOf(SupportedLanguages.class));
        admin.setDailyCapacity(null);
        try {
            return userRepository.save(admin);
        } catch (DataIntegrityViolationException ex) {
            throw new PublicSignupException(HttpStatus.CONFLICT, "Admin email is already registered");
        }
    }

    private InviteToken createInviteToken(User admin) {
        try {
            return inviteService.createInvite(admin);
        } catch (MailException ex) {
            throw new PublicSignupException(
                    HttpStatus.BAD_GATEWAY, "Failed to send invitation email. Please try again later.");
        }
    }
}
