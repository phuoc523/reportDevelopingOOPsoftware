package com.scentstation.operation.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scentstation.operation.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.operation.integration.identity.dto.IdentityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "operation.security.enabled", matchIfMissing = true)
public class OperationSecurityFilter extends OncePerRequestFilter {
    private static final Set<String> INTERNAL_POSTS = Set.of(
            "/api/inventory/consumptions",
            "/api/inventory/reconciliations",
            "/api/maintenance/alerts/critical",
            "/api/maintenance/post-tests/results"
    );
    private static final Set<String> ACTOR_FIELDS = Set.of(
            "actorId", "reportedByActorId", "diagnosedByActorId", "requestedByActorId",
            "reopenedByActorId", "completedByActorId", "closedByActorId", "assignedByActorId"
    );
    private static final Pattern TICKET_PATH = Pattern.compile("^/api/maintenance/(?:tickets/)?(\\d+)(?:/|$)");
    private static final Pattern MACHINE_HISTORY = Pattern.compile("^/api/maintenance/machines/(\\d+)/history$");

    private final IdentityIntrospectionClient identity;
    private final OperationAuthorization authorization;
    private final ObjectMapper objectMapper;
    private final String internalKey;

    public OperationSecurityFilter(
            IdentityIntrospectionClient identity,
            OperationAuthorization authorization,
            ObjectMapper objectMapper,
            @Value("${security.internal.service-key:}") String internalKey
    ) {
        this.identity = identity;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
        this.internalKey = internalKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/inventory/") && !path.startsWith("/api/maintenance/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if ("POST".equals(request.getMethod()) && INTERNAL_POSTS.contains(path)) {
                requireInternalKey(request.getHeader("X-Internal-Service-Key"));
                chain.doFilter(request, response);
                return;
            }

            IdentityPrincipal actor = identity.introspect(bearer(request));
            authorization.requirePermission(actor, requiredPermission(request.getMethod(), path));
            requireQueryTenant(actor, request.getParameter("tenantId"));
            request.setAttribute(OperationAuthorization.PRINCIPAL_ATTRIBUTE, actor);

            HttpServletRequest secured = secureBody(request, actor);
            requireMaintenanceScope(secured, actor);
            chain.doFilter(secured, response);
        } catch (ResponseStatusException exception) {
            response.sendError(exception.getStatusCode().value(), exception.getReason());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Malformed JSON request");
        }
    }

    private String bearer(HttpServletRequest request) {
        String value = request.getHeader("Authorization");
        if (value == null || !value.startsWith("Bearer ") || value.length() <= 7 || value.length() > 8192) {
            throw status(HttpStatus.UNAUTHORIZED, "Bearer token required");
        }
        return value.substring(7);
    }

    private String requiredPermission(String method, String path) {
        if (path.startsWith("/api/inventory/adjustments") && "POST".equals(method)) {
            return OperationAuthorization.INVENTORY_ADJUST;
        }
        if (path.startsWith("/api/inventory/")) {
            return "GET".equals(method) ? OperationAuthorization.INVENTORY_READ : OperationAuthorization.INVENTORY_MANAGE;
        }
        return "GET".equals(method) ? OperationAuthorization.MAINTENANCE_READ : OperationAuthorization.MAINTENANCE_MANAGE;
    }

    private void requireQueryTenant(IdentityPrincipal actor, String tenantId) {
        if (tenantId == null) return;
        try {
            authorization.requireTenant(actor, Long.valueOf(tenantId));
        } catch (NumberFormatException exception) {
            throw status(HttpStatus.FORBIDDEN, "Tenant access is not allowed");
        }
    }

    private HttpServletRequest secureBody(HttpServletRequest request, IdentityPrincipal actor) throws IOException {
        if ("GET".equals(request.getMethod()) || request.getContentLengthLong() == 0) return request;
        byte[] bytes = request.getInputStream().readAllBytes();
        if (bytes.length == 0) return new CachedBodyRequest(request, bytes);
        JsonNode parsed = objectMapper.readTree(bytes);
        if (!(parsed instanceof ObjectNode body)) return new CachedBodyRequest(request, bytes);

        JsonNode tenant = body.get("tenantId");
        if (tenant != null && (!tenant.canConvertToLong() || tenant.longValue() != actor.tenantId())) {
            throw status(HttpStatus.FORBIDDEN, "Tenant access is not allowed");
        }
        if (tenant != null) body.put("tenantId", actor.tenantId());
        ACTOR_FIELDS.forEach(field -> {
            if (body.has(field)) body.put(field, actor.id());
        });
        if (request.getRequestURI().endsWith("/assignment/acknowledge") && body.has("technicianActorId")) {
            body.put("technicianActorId", actor.id());
        }
        return new CachedBodyRequest(request, objectMapper.writeValueAsBytes(body));
    }

    private void requireMaintenanceScope(HttpServletRequest request, IdentityPrincipal actor) throws IOException {
        String path = request.getRequestURI();
        Matcher ticket = TICKET_PATH.matcher(path);
        if (ticket.find()) {
            authorization.requireTicket(actor, Long.valueOf(ticket.group(1)));
            return;
        }
        Matcher machine = MACHINE_HISTORY.matcher(path);
        if (machine.matches()) {
            authorization.requireMachineScope(actor, Long.valueOf(machine.group(1)), queryLong(request, "locationId"));
            return;
        }
        if ("POST".equals(request.getMethod()) && "/api/maintenance/tickets".equals(path)) {
            JsonNode body = objectMapper.readTree(request.getInputStream());
            authorization.requireMachineScope(actor, longField(body, "machineId"), nullableLongField(body, "locationId"));
        }
    }

    private Long queryLong(HttpServletRequest request, String name) {
        try { return request.getParameter(name) == null ? null : Long.valueOf(request.getParameter(name)); }
        catch (NumberFormatException exception) { return null; }
    }

    private Long longField(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.canConvertToLong()) throw status(HttpStatus.FORBIDDEN, "Machine scope is required");
        return value.longValue();
    }

    private Long nullableLongField(JsonNode body, String name) {
        JsonNode value = body.get(name);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
    }

    private void requireInternalKey(String supplied) {
        if (internalKey == null || internalKey.isBlank() || supplied == null || supplied.isBlank()
                || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw status(HttpStatus.UNAUTHORIZED, "A valid internal service key is required");
        }
    }

    private ResponseStatusException status(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
