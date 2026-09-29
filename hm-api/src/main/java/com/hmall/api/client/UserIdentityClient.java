package com.hmall.api.client;

import com.hmall.api.dto.customer.UserIdentityDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", contextId = "userIdentityClient")
public interface UserIdentityClient {
    @GetMapping("/internal/auth/identity")
    UserIdentityDTO resolve(@RequestHeader("X-Internal-Service-Secret") String serviceSecret,
            @RequestHeader("Authorization") String authorization);
}
