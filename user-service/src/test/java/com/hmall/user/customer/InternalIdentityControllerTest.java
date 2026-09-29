package com.hmall.user.customer;

import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.exception.UnauthorizedException;
import com.hmall.common.security.InternalServiceGuard;
import com.hmall.user.controller.InternalIdentityController;
import com.hmall.user.utils.JwtTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalIdentityControllerTest {
    private JwtTool tokens;
    private InternalIdentityController controller;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        tokens = new JwtTool(generator.generateKeyPair());
        controller = new InternalIdentityController(new InternalServiceGuard("secret"), tokens);
    }

    @Test
    void resolvesValidBearerTokenToMinimalIdentity() {
        String token = tokens.createToken(42L, Duration.ofMinutes(5));
        assertThat(controller.identity("secret", "Bearer " + token).getUserId()).isEqualTo(42L);
    }

    @Test
    void rejectsInvalidUserTokenAndMissingInternalSecret() {
        assertThatThrownBy(() -> controller.identity("secret", "Bearer invalid"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.identity(null, "Bearer token"))
                .isInstanceOf(ForbiddenException.class);
    }
}
