package com.leadsyncpro.dto;

import com.leadsyncpro.model.IntegrationConnectionStatus;
import com.leadsyncpro.model.IntegrationPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationStatusResponse {
    private IntegrationPlatform platform;
    private boolean connected;
    private Instant connectedAt;
    private Instant expiresAt;
    private Instant lastSyncedAt;
    private String platformPageId;
    private IntegrationConnectionStatus status;
    private String statusMessage;
    private Instant lastErrorAt;
    private String lastErrorMessage;
    private boolean requiresAction;
}
