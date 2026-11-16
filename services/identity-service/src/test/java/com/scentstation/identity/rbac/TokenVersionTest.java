package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenVersionTest {
    @Test void disablingAccountRevokesPreviousJwtVersion() {
        User u = new User("staff", "staff@example.com", "hash", "Staff", Role.STAFF);
        long oldVersion = u.getTokenVersion();
        u.setActive(false);
        assertTrue(u.getTokenVersion() > oldVersion);
    }
    @Test void roleChangeRevokesOldToken() {
        User u = new User("tech", "tech@example.com", "hash", "Tech", Role.TECHNICIAN);
        long oldVersion = u.getTokenVersion();
        u.setRoles(Set.of(Role.INVENTORY_STAFF));
        assertTrue(u.getTokenVersion() > oldVersion);
    }
}
