package com.leadsyncpro.repository;

import com.leadsyncpro.model.IntegrationLog;
import com.leadsyncpro.model.IntegrationPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IntegrationLogRepositoryTest {

    @Autowired
    private IntegrationLogRepository integrationLogRepository;

    @Test
    void findAllByOrganizationIdAndOptionalPlatform_returnsAllLogsForOrganizationWhenPlatformNull() {
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();

        IntegrationLog googleLog = createLog(organizationId, IntegrationPlatform.GOOGLE, Instant.parse("2024-01-01T10:15:30Z"));
        IntegrationLog facebookLog = createLog(organizationId, IntegrationPlatform.FACEBOOK, Instant.parse("2024-01-02T10:15:30Z"));
        IntegrationLog otherOrgLog = createLog(otherOrganizationId, IntegrationPlatform.FACEBOOK, Instant.parse("2024-01-03T10:15:30Z"));

        integrationLogRepository.saveAll(List.of(googleLog, facebookLog, otherOrgLog));

        List<IntegrationLog> logs = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, null, Sort.by(Sort.Direction.DESC, "startedAt"));

        assertThat(logs)
                .hasSize(2)
                .extracting(IntegrationLog::getPlatform)
                .containsExactly(IntegrationPlatform.FACEBOOK, IntegrationPlatform.GOOGLE);
    }

    @Test
    void findAllByOrganizationIdAndOptionalPlatform_filtersByPlatformWhenProvided() {
        UUID organizationId = UUID.randomUUID();

        IntegrationLog googleLog = createLog(organizationId, IntegrationPlatform.GOOGLE, Instant.parse("2024-02-01T08:00:00Z"));
        IntegrationLog facebookLog = createLog(organizationId, IntegrationPlatform.FACEBOOK, Instant.parse("2024-02-02T08:00:00Z"));

        integrationLogRepository.saveAll(List.of(googleLog, facebookLog));

        List<IntegrationLog> googleLogs = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, IntegrationPlatform.GOOGLE,
                        Sort.by(Sort.Direction.DESC, "startedAt"));

        assertThat(googleLogs)
                .hasSize(1)
                .first()
                .extracting(IntegrationLog::getPlatform)
                .isEqualTo(IntegrationPlatform.GOOGLE);

        List<IntegrationLog> facebookLogs = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, IntegrationPlatform.FACEBOOK,
                        Sort.by(Sort.Direction.DESC, "startedAt"));

        assertThat(facebookLogs)
                .hasSize(1)
                .first()
                .extracting(IntegrationLog::getPlatform)
                .isEqualTo(IntegrationPlatform.FACEBOOK);

        List<IntegrationLog> missingLogs = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(UUID.randomUUID(), IntegrationPlatform.GOOGLE,
                        Sort.by(Sort.Direction.DESC, "startedAt"));

        assertThat(missingLogs).isEmpty();
    }

    @Test
    void findAllByOrganizationIdAndOptionalPlatform_supportsPagination() {
        UUID organizationId = UUID.randomUUID();

        IntegrationLog firstLog = createLog(organizationId, IntegrationPlatform.GOOGLE, Instant.parse("2024-03-01T09:00:00Z"));
        IntegrationLog secondLog = createLog(organizationId, IntegrationPlatform.FACEBOOK, Instant.parse("2024-03-02T09:00:00Z"));
        IntegrationLog thirdLog = createLog(organizationId, IntegrationPlatform.FACEBOOK, Instant.parse("2024-03-03T09:00:00Z"));

        integrationLogRepository.saveAll(List.of(firstLog, secondLog, thirdLog));

        Page<IntegrationLog> firstPage = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, null,
                        PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "startedAt")));

        Page<IntegrationLog> secondPage = integrationLogRepository
                .findAllByOrganizationIdAndOptionalPlatform(organizationId, null,
                        PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "startedAt")));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(IntegrationLog::getStartedAt)
                .containsExactly(Instant.parse("2024-03-03T09:00:00Z"), Instant.parse("2024-03-02T09:00:00Z"));

        assertThat(secondPage.getContent())
                .extracting(IntegrationLog::getStartedAt)
                .containsExactly(Instant.parse("2024-03-01T09:00:00Z"));
    }

    private IntegrationLog createLog(UUID organizationId, IntegrationPlatform platform, Instant startedAt) {
        return IntegrationLog.builder()
                .organizationId(organizationId)
                .platform(platform)
                .startedAt(startedAt)
                .totalFetched(0)
                .newCreated(0)
                .updated(0)
                .errorMessage(null)
                .finishedAt(null)
                .build();
    }
}
