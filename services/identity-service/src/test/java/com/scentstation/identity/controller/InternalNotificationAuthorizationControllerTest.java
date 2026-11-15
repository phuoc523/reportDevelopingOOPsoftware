package com.scentstation.identity.controller;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalNotificationAuthorizationControllerTest {
    private final AccessPolicy access = mock(AccessPolicy.class);
    private final UserRepository users = mock(UserRepository.class);
    private final InternalNotificationAuthorizationController controller =
        new InternalNotificationAuthorizationController(access, users, "private-key");

    @Test
    void authorizesUserAndReturnsOnlyRecipientsInTheirTenant() {
        AccountPrincipal actor = new AccountPrincipal(1L, "admin", 2L,
            Set.of(Role.BRAND_ADMIN), Set.of(Permission.NOTIFICATION_CONFIGURE), Set.of(), Set.of());
        User recipient = new User("recipient", "recipient@example.com", "hash", "Recipient", Role.VIEWER);
        recipient.setTenantId(2L);
        setId(recipient, 7L);
        when(access.current()).thenReturn(actor);
        when(users.findAllById(any())).thenReturn(List.of(recipient));

        var response = controller.authorizeUser("private-key",
            new InternalNotificationAuthorizationController.AuthorizationRequest(
                "2", "NOTIFICATION_CONFIGURE", List.of("7")));

        assertEquals("USER", response.callerType());
        assertEquals(1L, response.userId());
        assertEquals(List.of("7"), response.validatedRecipientIds());
    }

    @Test
    void rejectsServiceWithWrongCredential() {
        var request = new InternalNotificationAuthorizationController.AuthorizationRequest(
            "2", "NOTIFICATION_READ", List.of());
        assertThrows(ResponseStatusException.class,
            () -> controller.authorizeService("private-key", "machine-service", "wrong", request));
        verifyNoInteractions(users, access);
    }

    @Test
    void resolvesOnlyActiveRecipientsWithRequestedPermission() {
        User reader = user(7L, "reader", Role.VIEWER, true, false);
        User customer = user(8L, "customer", Role.CUSTOMER, true, false);
        User lockedReader = user(9L, "locked", Role.VIEWER, true, true);
        when(users.findByTenantId(2L)).thenReturn(List.of(reader, customer, lockedReader));

        var response = controller.resolveRecipients("private-key", "notification-service", "private-key",
            new InternalNotificationAuthorizationController.AuthorizationRequest(
                "2", "NOTIFICATION_READ", List.of()));

        assertEquals(List.of("7"), response.validatedRecipientIds());
    }

    private User user(Long id, String username, Role role, boolean active, boolean locked) {
        User user = new User(username, username + "@example.com", "hash", username, role);
        user.setTenantId(2L);
        if (!active) user.setActive(false);
        if (locked) user.setLocked(true);
        setId(user, id);
        return user;
    }

    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
