package com.hmall.customer.chat;

import lombok.Getter;

@Getter
public class CustomerActor {
    private final Long userId;
    private final String guestKey;

    public CustomerActor(Long userId, String guestKey) {
        this.userId = userId;
        this.guestKey = guestKey;
    }

    public boolean authenticated() { return userId != null; }
}
