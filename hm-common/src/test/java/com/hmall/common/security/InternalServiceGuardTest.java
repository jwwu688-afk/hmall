package com.hmall.common.security;

import com.hmall.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServiceGuardTest {
    private final InternalServiceGuard guard = new InternalServiceGuard("internal-test-secret");

    @Test
    void rejectsMissingOrWrongSecret() {
        assertThatThrownBy(() -> guard.verify(null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> guard.verify("wrong")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptsExactSecret() {
        guard.verify("internal-test-secret");
    }
}
