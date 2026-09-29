package com.hmall.customer.chat;

import com.hmall.api.client.UserIdentityClient;
import com.hmall.api.dto.customer.UserIdentityDTO;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerIdentityService {
    private final UserIdentityClient identities;
    private final String internalSecret;

    public CustomerIdentityService(UserIdentityClient identities,
            @Value("${hm.internal.service-secret}") String internalSecret) {
        this.identities = identities;
        this.internalSecret = internalSecret;
    }

    public CustomerActor actor(String authorization, String guestKey) {
        if (authorization == null || authorization.isBlank()) {
            return new CustomerActor(null, guestKey);
        }
        try {
            UserIdentityDTO identity = identities.resolve(internalSecret, authorization);
            if (identity == null || identity.getUserId() == null) {
                throw unauthorized();
            }
            return new CustomerActor(identity.getUserId(), null);
        } catch (FeignException error) {
            if (error.status() == 401 || error.status() == 403) {
                throw unauthorized();
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "用户认证服务暂时不可用", error);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录凭证无效");
    }
}
