package com.leadsyncpro.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkAssignRequest {
    private List<UUID> leadIds;
    private UUID userId;
}

