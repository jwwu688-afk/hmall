package com.hmall.customer.policy;

import com.hmall.customer.query.CustomerDelegationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/customer/policies")
@RequiredArgsConstructor
public class CustomerPolicyController {
    private final CustomerDelegationTokenService tokens;
    private final CustomerPolicyService policies;

    @GetMapping
    public List<PolicyExcerpt> search(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category) {
        tokens.verify(authorization, "policy:read");
        return policies.search(query, category);
    }
}
