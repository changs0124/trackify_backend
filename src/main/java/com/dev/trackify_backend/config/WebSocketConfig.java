package com.dev.trackify_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // SimpleBroker 하트비트 전송용 스케줄러(가벼운 작업이므로 1스레드로 충분)
    @Bean(name = "wsBrokerTaskScheduler")
    public TaskScheduler wsBrokerTaskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1);
        ts.setThreadNamePrefix("ws-heartbeat-");
        ts.initialize();
        return ts;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // RN + stompjs 호환을 위해 STOMP 서브프로토콜 명시
        DefaultHandshakeHandler hs = new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
        hs.setSupportedProtocols("v12.stomp","v11.stomp","v10.stomp");

        registry.addEndpoint("/ws")
                .setHandshakeHandler(hs)
                .setAllowedOriginPatterns("*"); // CORS: 필요한 도메인만 허용하도록 차후 좁히는 걸 권장
                                                // SockJS 미사용(RN은 순수 WebSocket 사용)
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");

        var simple = registry.enableSimpleBroker("/topic", "/queue");
        simple.setTaskScheduler(wsBrokerTaskScheduler());
        simple.setHeartbeatValue(new long[]{10_000, 10_000}); // [server > client, client > server]
    }
}
