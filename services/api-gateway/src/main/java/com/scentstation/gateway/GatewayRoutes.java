package com.scentstation.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class GatewayRoutes {
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
       @Value("${gateway.identity-url:http://127.0.0.1:8081}") String identity,
       @Value("${gateway.catalog-url:}") String catalog,
       @Value("${gateway.operation-url:}") String operation,
       @Value("${gateway.order-url:}") String order,
       @Value("${gateway.machine-url:}") String machine,
       @Value("${gateway.reporting-url:}") String reporting) {
        var routes=builder.routes();
        Map<String,String> backendUrls=Map.of(
            "identity",identity,"catalog",catalog,"operation",operation,
            "order",order,"machine",machine,"reporting",reporting);
        backendUrls.forEach((name,url)->{
            if(url==null||url.isBlank())return; // unconfirmed services are NOT exposed
            if(!url.startsWith("http://")&&!url.startsWith("https://"))
                throw new IllegalArgumentException("Invalid gateway backend URL for "+name);
            String id=name;
            routes.route(id, r->r.path("/api/v1/"+id+"/**")
                .filters(f->f.rewritePath("/api/v1/"+id+"/(?<rest>.*)", "/api/v1/${rest}"))
                .uri(url));
        });
        // Legacy OOP-12 aliases; preserve existing /api/v1/identity/** routes.
        routes.route("identity-auth-legacy", r -> r.path("/api/auth/**")
            .filters(f -> f.rewritePath("/api/auth/(?<rest>.*)", "/api/v1/auth/${rest}"))
            .uri(identity));
        // Narrow admin alias: only RBAC operations; never proxy arbitrary internal paths.
        routes.route("identity-admin-rbac-legacy", r -> r.path("/api/admin/rbac/**")
            .filters(f -> f.rewritePath("/api/admin/rbac/(?<rest>.*)", "/api/v1/rbac/${rest}"))
            .uri(identity));
        return routes.build();
    }
}
