package com.hmall.common.security;

import com.hmall.common.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceGuard {
    private final String expectedSecret;

    public InternalServiceGuard(@Value("${hm.internal.service-secret:}") String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    public void verify(String presentedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank() || presentedSecret == null
                || !MessageDigest.isEqual(expectedSecret.getBytes(StandardCharsets.UTF_8),
                presentedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("内部服务凭证无效");
        }
    }
}
