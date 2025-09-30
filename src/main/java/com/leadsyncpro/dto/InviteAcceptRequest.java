package com.leadsyncpro.dto;

import lombok.Data;

@Data
public class InviteAcceptRequest {
    private String token;
    private String password;
}