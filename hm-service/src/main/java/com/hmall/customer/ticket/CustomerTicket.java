package com.hmall.customer.ticket;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CustomerTicket {
    private String ticketId;
    private String status;
    private String conversationId;
    private Long orderId;
    private String summary;
    private LocalDateTime createdAt;
}
