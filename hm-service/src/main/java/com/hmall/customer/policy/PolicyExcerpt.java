package com.hmall.customer.policy;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PolicyExcerpt {
    private Long policyId;
    private Integer version;
    private String title;
    private LocalDateTime effectiveFrom;
    private String category;
    private String excerpt;
}
