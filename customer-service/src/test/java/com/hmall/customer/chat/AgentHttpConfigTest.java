package com.hmall.customer.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHttpConfigTest {
    @Test
    void agentClientHasBoundedConnectAndReadTimeouts() {
        RestTemplate rest = new AgentHttpConfig().agentRestTemplate(
                Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertThat(rest.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        Object factory = rest.getRequestFactory();
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(5000);
    }
}
