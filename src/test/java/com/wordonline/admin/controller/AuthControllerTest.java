package com.wordonline.admin.controller;

import com.wordonline.admin.client.AccountServerClient;
import com.wordonline.admin.dto.auth.TokenResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AccountServerClient account;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void loginRendersTheThemeAndOnlyRestoresWhenAppropriate() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/theevilent-theme.css\"")))
                .andExpect(content().string(containsString("data-session-refresh=\"/login/session/refresh\"")));
        mvc.perform(get("/login").param("signedOut", "true")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-session-refresh="))));
    }

    @Test
    void refreshAndLogoutUseTheScopedRefreshCookie() throws Exception {
        when(account.login("manager", "password")).thenReturn(new TokenResponseDto("access", "refresh", 3600));
        when(account.refresh("refresh")).thenReturn(new TokenResponseDto("new-access", "new-refresh", 3600));

        mvc.perform(post("/login").param("username", "manager").param("password", "password"))
                .andExpect(redirectedUrl("/"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, contains(containsString("jwt=access"),
                        allOf(containsString("adminRefreshToken=refresh"), containsString("Path=/login/session"),
                                containsString("Max-Age=5184000"), containsString("HttpOnly"),
                                containsString("Secure"), containsString("SameSite=Strict")))));
        mvc.perform(post("/login/session/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("adminRefreshToken", "refresh")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/login/session/refresh").header("X-Console-Session", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/login/session/refresh").header("X-Console-Session", "1")
                        .cookie(new jakarta.servlet.http.Cookie("adminRefreshToken", "refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, contains(containsString("jwt=new-access"),
                        containsString("adminRefreshToken=new-refresh"))));
        mvc.perform(post("/login/session/logout").header("X-Console-Session", "1")
                        .cookie(new jakarta.servlet.http.Cookie("adminRefreshToken", "new-refresh")))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, contains(containsString("Max-Age=0"),
                        allOf(containsString("Path=/login/session"), containsString("Max-Age=0")))));
        verify(account).logout("new-refresh");
    }
}
