package com.wordonline.admin.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponseDto {
    private String jwt;
    private String refreshToken;
    private long expiresIn;
}
