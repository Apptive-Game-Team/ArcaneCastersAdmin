package com.wordonline.admin.config;

import com.wordonline.admin.controller.DefaultContentController;
import com.wordonline.admin.security.JwtAuthenticationFilter;
import com.wordonline.admin.service.DefaultContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DefaultContentController.class)
@Import({WebSecurityConfig.class, JwtAuthenticationFilter.class})
class WebSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefaultContentService defaultContentService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousApiCallGetsUnauthorizedInsteadOfALoginRedirect() throws Exception {
        mockMvc.perform(post("/api/admin/grant-default-contents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPageRequestStillRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/invalidate-cache"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }
}
