package com.hmall.service.impl;

import com.hmall.utils.JwtTool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemServiceImplTest {

    @Test
    void testJwt() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        JwtTool jwtTool = new JwtTool(generator.generateKeyPair());
        String token = jwtTool.createToken(1L, Duration.ofMinutes(30));
        assertEquals(1L, jwtTool.parseToken(token));
    }

    @Test
    @Disabled("manual integration test: requires a prepared MySQL inventory dataset")
    void deductStock() {
        // Intentionally kept as a named manual scenario; automated tests must not mutate shared inventory.
    }
}
