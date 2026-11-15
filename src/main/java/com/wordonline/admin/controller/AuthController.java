package com.wordonline.admin.controller;

import com.wordonline.admin.client.AccountServerClient;
import com.wordonline.admin.dto.auth.TokenResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "adminRefreshToken";
    private static final String SESSION_PATH = "/login/session";
    private static final String SESSION_HEADER = "X-Console-Session=1";
    private final AccountServerClient accountServerClient;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String signedOut, Model model) {
        model.addAttribute("restoreSession", signedOut == null);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletResponse response,
                        Model model) {
        try {
            writeSession(response, accountServerClient.login(username, password));
            return "redirect:/";
        } catch (RuntimeException e) {
            model.addAttribute("error", "Could not sign in. Check your credentials or try again.");
            return "login";
        }
    }

    @PostMapping(path = SESSION_PATH + "/refresh", headers = SESSION_HEADER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                        HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            writeSession(response, accountServerClient.refresh(refreshToken));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @PostMapping(path = SESSION_PATH + "/logout", headers = SESSION_HEADER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                accountServerClient.logout(refreshToken);
            } catch (RuntimeException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            }
        }
        addCookie(response, "jwt", "", "/", Duration.ZERO);
        addCookie(response, REFRESH_COOKIE, "", SESSION_PATH, Duration.ZERO);
    }

    private static void writeSession(HttpServletResponse response, TokenResponseDto tokens) {
        if (tokens == null || tokens.getJwt() == null || tokens.getRefreshToken() == null
                || tokens.getExpiresIn() <= 0) {
            throw new IllegalStateException("Account server did not return a session");
        }
        addCookie(response, "jwt", tokens.getJwt(), "/", Duration.ofSeconds(tokens.getExpiresIn()));
        addCookie(response, REFRESH_COOKIE, tokens.getRefreshToken(), SESSION_PATH, Duration.ofDays(60));
    }

    private static void addCookie(HttpServletResponse response, String name, String value,
                                  String path, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .path(path).secure(true).httpOnly(true).sameSite("Strict").maxAge(maxAge)
                .build().toString());
    }
}
