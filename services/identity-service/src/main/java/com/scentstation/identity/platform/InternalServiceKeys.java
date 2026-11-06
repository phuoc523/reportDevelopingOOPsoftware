package com.scentstation.identity.platform;

import com.scentstation.identity.rbac.RbacException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Key check is separate from staff Bearer authentication; internal endpoints must not be publicly routed. */
public final class InternalServiceKeys {
    private InternalServiceKeys(){}
    public static void verify(String expected,String provided){
        if(expected==null||expected.length()<32)throw new RbacException(HttpStatus.SERVICE_UNAVAILABLE,"INTERNAL_KEY_NOT_CONFIGURED");
        byte[] expectedBytes=expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes=provided==null?new byte[0]:provided.getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expectedBytes,providedBytes))throw new RbacException(HttpStatus.UNAUTHORIZED,"SERVICE_UNAUTHENTICATED");
    }
}
