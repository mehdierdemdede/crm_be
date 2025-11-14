package com.leadsyncpro.billing.facade;

public interface PublicSignupFacade {

    PublicSignupResult createSignup(CreatePublicSignupCmd command);
}
