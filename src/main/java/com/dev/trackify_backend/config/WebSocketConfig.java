package com.dev.trackify_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 간단한 Principal 구현
    static final class StompPrincipal implements Principal {
        private final String name;
        StompPrincipal(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    @Bean(name = "wsBrokerTaskScheduler")
    public TaskScheduler wsBrokerTaskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1);
        ts.setThreadNamePrefix("ws-heartbeat-");
        ts.initialize();
        return ts;
    }

    // ★ Handshake에서 Principal을 생성
    @Bean
    public DefaultHandshakeHandler userHandshakeHandler() {
        return new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()) {
            @Override
            protected Principal determineUser(ServerHttpRequest request,
                                              WebSocketHandler wsHandler,
                                              Map<String, Object> attributes) {
                MultiValueMap<String, String> qs =
                        UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
                String raw = (qs != null ? qs.getFirst("userCode") : null);
                String code = (raw == null || raw.isBlank())
                        ? "anon-" + UUID.randomUUID()
                        : raw.trim().toLowerCase();
                return new StompPrincipal(code); // ← SimpUserRegistry에 이 이름으로 올라감
            }
        };
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var hs = userHandshakeHandler();
        // STOMP 서브프로토콜 유지
        hs.setSupportedProtocols("v12.stomp","v11.stomp","v10.stomp");

        registry.addEndpoint("/ws")
                .setHandshakeHandler(hs)
                .setAllowedOriginPatterns("*"); // 필요시 제한
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        var simple = registry.enableSimpleBroker("/topic", "/queue");
        simple.setTaskScheduler(wsBrokerTaskScheduler());
        simple.setHeartbeatValue(new long[]{10_000, 10_000});
        // registry.setUserDestinationPrefix("/user"); // 기본값 /user
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 여기는 비워도 됩니다(Handshake에서 이미 Principal 부여)
    }
}
