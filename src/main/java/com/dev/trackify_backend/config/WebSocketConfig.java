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
/** 역할
 *  STOMP 엔드포인트 등록: /ws로 들어오는 WebSocket 업그레이드 요청 받음
 * 사용자 식별자 주입: 핸드셰이크 단계에서 쿼리 파라미터(?userCode=)를 읽어 Principal 이름으로 설정
 * 메시지 라우팅 규칙 정의: /app/** @MessageMapping, /topic, /queues는 SimpleBroker
 * 하트비트 스케줄링: 서버와 클라이언트 간 하트비트(10초)로 유휴 연결/네트워크 단절 탐지
 * CORS/Origin 제어: 필요 시 도메인 제한 가능
 * */
@Configuration
@EnableWebSocketMessageBroker // STOMP 메시징 활성화(컨트롤러 @MessageMapping 등 사용 가능)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    // 핸드셰이크 시 Principal로 쓸 간단한 구현체
    // - Spring의 STOMP는 Principal.getName()을 "사용자 식별자"로 사용
    // - /user/{principalName}/queue/** 전송 시 이 값이 수신자 ID가 됨
    static final class StompPrincipal implements Principal {
        private final String name; // Principal.getName() > userCode
        StompPrincipal(String name) { this.name = name; }

        @Override
        public String getName() {
            return name;
        }
    }

    // SimpleBroker 하트비트 스케줄러 빈
    // - 내장 SimpleBroker가 하트비트를 보낼 때 사용할 스케줄러
    @Bean(name = "wsBrokerTaskScheduler")
    public TaskScheduler wsBrokerTaskScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1); // 가벼운 작업이라 1개면 충분
        ts.setThreadNamePrefix("ws-heartbeat-"); // 디버깅/모니터링용 스레드 접두사
        ts.initialize(); // 실제 스레드풀 초기화
        return ts;
    }

    // 핸드셰이크 단계에서 Principal 결정하는 핸들러
    // - ws://.../ws?userCode=xxx 쿼리에서 userCode 추출 > Principal 이름으로 사용
    // - 빈값/누락 시 annon-UUID 발급(게스트 세션 같은 시나리오 대비)
    @Bean
    public DefaultHandshakeHandler userHandshakeHandler() {
        return new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy()) {
            @Override
            protected Principal determineUser(
                    ServerHttpRequest request, // 클라이언트의 WS 업그레이드 요청
                    WebSocketHandler wsHandler, // 핸드셰이크 대상 핸들러
                    Map<String, Object> attributes // 핸드셰이크 동안 공유되는 임시 속성
            ) {
                // 요청 URI에서 쿼리 파라미터 추출
                MultiValueMap<String, String> qs =
                        UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();

                // ?userCode=... 획득
                String raw = (qs != null ? qs.getFirst("userCode") : null);

                // 정상값이면 trim→소문자 normalize, 없으면 anon-UUID
                String code = (raw == null || raw.isBlank())
                        ? "anon-" + UUID.randomUUID()
                        : raw.trim().toLowerCase();

                // 여기서 반환되는 Principal.getName()이 SimpUserRegistry에 사용자명으로 등록
                // - convertAndSendToUser("userCode", "/queue/events", payload)로 1:1 전송 가능
                return new StompPrincipal(code);
            }
        };
    }

    // STOMP 엔드포인트 등록
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 위에서 정의한 핸드셰이크 핸들러 사용
        var hs = userHandshakeHandler();

        // 클라이언트와 협상할 STOMP 서브프로토콜 명시(v12 우선)
        hs.setSupportedProtocols("v12.stomp","v11.stomp","v10.stomp");

        registry.addEndpoint("/ws") // 웹소켓 업그레이드 엔드포인트(예: ws://host/ws)
                .setHandshakeHandler(hs) // 핸드셰이크 시 Principal 주입
                .setAllowedOriginPatterns("*"); // CORS/Origin 허용(운영에서는 도메인 화이트리스트 권장)
        // .withSockJS(), SockJS 플백이 필요하면 활성화
    }
    
    // 메시지 브로커/라우팅 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 애플리케이션 핸들러 수신 프리픽스
        // - 클라이언트가 /app/xxx 로 send @MessageMapping("/xxx") 메서드로 라우팅
        registry.setApplicationDestinationPrefixes("/app");
        
        // 내장 SimpleBroker 활성화
        // - /topic, /queue 로 시작하는 목적지는 브로커가 직접 처리
        var simple = registry.enableSimpleBroker("/topic", "/queue");

        // 하트비트 세팅(서버-클라 각각 10초 간격)
        // - 연결 유휴/단절 탐지, 유령 세선 방지
        simple.setTaskScheduler(wsBrokerTaskScheduler());
        simple.setHeartbeatValue(new long[]{10_000, 10_000});
    }
    
    // 인바운드 채널 인터셉터 등 커스터마이즈 지점
    // - 현재는 Principal 부여를 핸듯셰이크에서 끝냈기에 비워둠
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 필요 시: 인증/권한/로깅/레이트리밋 인터셉터 추가 가능
        // registration.interceptors(new YourChannelInterceptor());
    }
}
