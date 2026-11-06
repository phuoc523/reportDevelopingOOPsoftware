package com.scentstation.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/** Real HTTP proxy contract with a local, disposable backend; no team service port assumptions. */
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"gateway.catalog-url=","gateway.order-url=","gateway.reporting-url="})
class GatewayContractTest {
    private static final DisposableServer BACKEND = HttpServer.create().host("127.0.0.1").port(0)
        .handle((request,response) -> response
            .header("X-Test-Upstream-Path",request.uri())
            .header("X-Test-Verified-Tenant",request.requestHeaders().contains("X-Verified-Tenant-Id")?"leaked":"removed")
            .header("X-Test-Service-Key",request.requestHeaders().contains("X-Internal-Service-Key")?"leaked":"removed")
            .header("X-Test-Request-Id",request.requestHeaders().get("X-Request-Id") == null ? "missing" : "present")
            .sendString(Mono.just("ok")))
        .bindNow();
    @DynamicPropertySource
    static void identityBackend(DynamicPropertyRegistry props){
        props.add("gateway.identity-url",()->"http://127.0.0.1:"+BACKEND.port());
    }
    @AfterAll static void shutdown(){BACKEND.disposeNow();}

    @Autowired WebTestClient client;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    private WebTestClient web(){return client;}

    @Test void healthWorks(){
        web().get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
    @Test void unknownRouteIsNotFound(){
        web().get().uri("/api/v1/nonexistent/item").exchange().expectStatus().isNotFound();
    }
    @Test void internalApiIsNotPublic(){
        web().post().uri("/api/v1/identity/internal/tenant/introspect")
            .exchange().expectStatus().isNotFound();
    }
    @Test void corsAllowsConfiguredFrontend(){
        web().options().uri("http://127.0.0.1:" + port + "/api/v1/identity/auth/login")
            .header(HttpHeaders.ORIGIN,"http://localhost:5173")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,"POST")
            .exchange().expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"http://localhost:5173");
    }
    @Test void legacyAuthAliasRewritesToVersionedIdentity(){
        web().post().uri("/api/auth/login").exchange().expectStatus().isOk()
            .expectHeader().valueEquals("X-Test-Upstream-Path", "/api/v1/auth/login");
    }
    @Test void legacyAdminRbacAliasRewritesToRbac(){
        web().get().uri("/api/admin/rbac/me").exchange().expectStatus().isOk()
            .expectHeader().valueEquals("X-Test-Upstream-Path", "/api/v1/rbac/me");
    }
    @Test void legacyAdminDoesNotExposeArbitraryApi(){
        web().get().uri("/api/admin/internal/tenant/introspect").exchange().expectStatus().isNotFound();
    }
    @Test void proxyRewritesPathAndScrubsSpoofedInternalHeaders(){
        web().get().uri("/api/v1/identity/rbac/me")
            .header("X-Verified-Tenant-Id","FAKE_TENANT")
            .header("X-Internal-Service-Key","FAKE_KEY")
            .header("X-Request-Id","client-spoof")
            .exchange().expectStatus().isOk()
            .expectHeader().valueEquals("X-Test-Upstream-Path","/api/v1/rbac/me")
            .expectHeader().valueEquals("X-Test-Verified-Tenant","removed")
            .expectHeader().valueEquals("X-Test-Service-Key","removed")
            .expectHeader().valueEquals("X-Test-Request-Id","present");
    }
}
