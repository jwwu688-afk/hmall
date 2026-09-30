package com.hmall.user.controller;

import com.hmall.api.dto.customer.UserIdentityDTO;
import com.hmall.common.exception.UnauthorizedException;
import com.hmall.common.security.InternalServiceGuard;
import com.hmall.user.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalIdentityController {
    private final InternalServiceGuard guard;
    private final JwtTool tokens;

    @GetMapping("/identity")
    public UserIdentityDTO identity(
            @RequestHeader(value = "X-Internal-Service-Secret", required = false) String serviceSecret,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        guard.verify(serviceSecret);
        if (authorization == null || authorization.isBlank()) {
            throw new UnauthorizedException("未登录");
        }
        String token = authorization.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        if (token.isEmpty()) {
            throw new UnauthorizedException("未登录");
        }
        UserIdentityDTO identity = new UserIdentityDTO();
        identity.setUserId(tokens.parseToken(token));
        return identity;
    }
}
