package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FacebookAdHierarchyResponse {
    @Singular
    List<FacebookPageNodeResponse> pages;
}

