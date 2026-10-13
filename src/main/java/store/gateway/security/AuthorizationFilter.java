package store.gateway.security;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AuthorizationFilter implements GlobalFilter {

    public static String AUTH_COOKIE_TOKEN = "__store_jwt_token";
    public static String AUTH_SERVICE_TOKEN_SOLVE = "http://auth:8080/auth/solve";

    @Autowired
    private RouterValidator routerValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!routerValidator.isSecured.test(request)) {
            return chain.filter(exchange);
        }

        if (request.getCookies().containsKey(AUTH_COOKIE_TOKEN)) {
            String token = request.getCookies().getFirst(AUTH_COOKIE_TOKEN).getValue();
            if (null != token && token.length() > 0) {
                return requestAuthTokenSolve(exchange, chain, token.trim());
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> requestAuthTokenSolve(ServerWebExchange exchange, GatewayFilterChain chain, String jwt) {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri(AUTH_SERVICE_TOKEN_SOLVE)
            .bodyValue(Map.of("token", jwt))
            .retrieve()
            .toEntity(Map.class)
            .flatMap(response -> {
                if (response != null && response.hasBody() && response.getBody() != null) {
                    final Map<String, String> map = response.getBody();
                    String idAccount = map.get("idAccount");
                    ServerWebExchange authorized = updateRequest(exchange, idAccount, jwt);
                    return chain.filter(authorized);
                } else {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
                }
            });
    }

    private ServerWebExchange updateRequest(ServerWebExchange exchange, String idAccount, String jwt) {
        return exchange.mutate()
            .request(
                exchange.getRequest()
                    .mutate()
                    .header("id-account", idAccount)
                    .header("Authorization", "Bearer " + jwt)
                    .build()
            ).build();
    }

}