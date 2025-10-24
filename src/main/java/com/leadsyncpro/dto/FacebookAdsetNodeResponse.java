package com.leadsyncpro.dto;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FacebookAdsetNodeResponse {
    String adsetId;
    String adsetName;
    @Singular
    List<FacebookAdNodeResponse> ads;
}

