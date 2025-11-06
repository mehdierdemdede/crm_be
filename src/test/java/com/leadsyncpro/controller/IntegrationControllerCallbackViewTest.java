package com.leadsyncpro.controller;

import com.leadsyncpro.model.Role;
import com.leadsyncpro.repository.IntegrationLogRepository;
import com.leadsyncpro.security.UserPrincipal;
import com.leadsyncpro.service.IntegrationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "app.oauth2.frontend-success-redirect-url=https://app.example.com/success",
        "app.oauth2.frontend-error-redirect-url=https://app.example.com/error"
})
class IntegrationControllerCallbackViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IntegrationService integrationService;

    @MockBean
    private IntegrationLogRepository integrationLogRepository;

    @Test
    void oauth2CallbackSuccessRendersSuccessTemplate() throws Exception {
        Mockito.doNothing()
                .when(integrationService)
                .handleOAuth2Callback("facebook", "code-123", "state-xyz", "http://localhost");

        mockMvc.perform(get("/api/integrations/oauth2/callback/facebook")
                .param("code", "code-123")
                .param("state", "state-xyz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("integration-success template")))
                .andExpect(content().string(containsString("Facebook bağlantısı başarılı!")))
                .andExpect(content().string(containsString("crm-pro-oauth")));

        verify(integrationService).handleOAuth2Callback("facebook", "code-123", "state-xyz", "http://localhost");
    }

    @Test
    void oauth2CallbackErrorRendersErrorTemplate() throws Exception {
        mockMvc.perform(get("/api/integrations/oauth2/callback/facebook")
                        .param("error", "access_denied"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("integration-error template")))
                .andExpect(content().string(containsString("OAuth2 işlemi tamamlanamadı")))
                .andExpect(content().string(containsString("Bağlantı başarısız")));
    }

    @Test
    void getIntegrationWithInvalidPlatformReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId,
                organizationId,
                "admin@example.com",
                "password",
                Role.ADMIN,
                List.of(new SimpleGrantedAuthority(Role.ADMIN.name()))
        );

        TestingAuthenticationToken authenticationToken = new TestingAuthenticationToken(
                principal,
                null,
                Role.ADMIN.name());
        authenticationToken.setAuthenticated(true);

        mockMvc.perform(get("/api/integrations/invalid")
                        .with(authentication(authenticationToken)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(integrationService);
    }
}
