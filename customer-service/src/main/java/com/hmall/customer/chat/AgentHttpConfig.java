package com.hmall.customer.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AgentHttpConfig {
    @Bean("agentRestTemplate")
    public RestTemplate agentRestTemplate(
            @Value("${hm.agent.connect-timeout:2s}") Duration connectTimeout,
            @Value("${hm.agent.read-timeout:5s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
        return new RestTemplate(factory);
    }
}
