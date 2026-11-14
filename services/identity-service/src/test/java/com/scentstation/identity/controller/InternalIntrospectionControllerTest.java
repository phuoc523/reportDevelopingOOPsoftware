package com.scentstation.identity.controller;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalIntrospectionControllerTest {
    private final AccessPolicy access = mock(AccessPolicy.class);
    private final AccountPrincipal actor = new AccountPrincipal(1L, "user", 2L,
        Set.of(Role.TECHNICIAN), Set.of(Permission.DIAGNOSTIC_SPRAY), Set.of(10L), Set.of(20L));

    @Test void failsClosedWithNoConfiguredServiceKey() {
        var controller = new InternalIntrospectionController(access, "");
        assertThrows(ResponseStatusException.class, () -> controller.introspect("anything"));
        verifyNoInteractions(access);
    }

    @Test void rejectsIncorrectOrMissingServiceKey() {
        var controller = new InternalIntrospectionController(access, "private-key");
        assertThrows(ResponseStatusException.class, () -> controller.introspect(null));
        assertThrows(ResponseStatusException.class, () -> controller.introspect("wrong"));
        verifyNoInteractions(access);
    }

    @Test void returnsFreshValidatedPrincipalForTrustedCaller() {
        when(access.current()).thenReturn(actor);
        var controller = new InternalIntrospectionController(access, "private-key");
        assertSame(actor, controller.introspect("private-key"));
        verify(access).current();
    }
}
