package com.hmall.customer.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerConversationServiceTest {
    @Test
    void guest_key_does_not_match_another_guest() {
        CustomerActor actor = new CustomerActor(null, "guest-one");
        assertFalse(CustomerConversationService.owns(actor, null,
                CustomerConversationService.hashGuestKey("guest-two")));
        assertTrue(CustomerConversationService.owns(actor, null,
                CustomerConversationService.hashGuestKey("guest-one")));
    }

    @Test
    void logged_in_user_cannot_access_other_users_conversation() {
        assertFalse(CustomerConversationService.owns(new CustomerActor(1L, null), 2L, null));
        assertTrue(CustomerConversationService.owns(new CustomerActor(1L, null), 1L, null));
    }

    @Test
    void out_of_order_event_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CustomerConversationService.requireNextSequence(4, 6));
        assertDoesNotThrow(() -> CustomerConversationService.requireNextSequence(4, 5));
    }
}
