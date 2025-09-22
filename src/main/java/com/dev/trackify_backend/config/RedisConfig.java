package com.dev.trackify_backend.config;

import com.dev.trackify_backend.status.PresenceStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Spring Boot와 Redis 연결을 위한 설정 클래스
 * RedisConnectionFactory를 만들고 RedisTemplate을 정의해서 Redis 서버와 직렬화/역직렬화 방식을 정함
 * @Autowired로 RedisTemplate을 주입받아 Redis 데이터를 쉽게 추가/수정/삭제 가능
 * */
@Configuration
public class RedisConfig {
    // 프로퍼티 주입
    // @Value: .yml/.properties에 있는 설정값을 가지고 옴
    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    // RedisConnectionFactory: Spring과 Redis 서버를 연결할 때 사용하는 커넥션 팩토리
    // RedisStandaloneConfiguration: 단일 Redis 서버에 연결할 때 설정(host, port, password)
    // LettuceConnectionFactory: Lettuce 클라이언트를 이용해서 Redis 연결 관리
    // - Lettuce는 비동기/스레드 세이프 > 고성능
    // - Spring boot 기본도 Lettuce
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            conf.setPassword(password);
        }
        return new LettuceConnectionFactory(conf);
    }

    // RedisTemplate<K, V>: Spring에서 Redis 작업(Key-Value)을 쉽게 도와주는 템플릿 클래스
    @Bean
    public RedisTemplate<String, PresenceStatus.Presence> presenceRedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, PresenceStatus.Presence> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(cf);
        
        // Redis는 기본적으로 바이트 배열만 저장 가능
        // 객체 저장을 위해서 직렬화(serializer)필요
        // StringRedisSerializer: Key를 문자열로 저장/조회
        StringRedisSerializer keySer = new StringRedisSerializer();
        
        // Value를 JSON 문자열로 변환 > 사람이 읽기 편하고, 객체 - JSON 변환도 쉬움
        Jackson2JsonRedisSerializer<PresenceStatus.Presence> valSer =
                new Jackson2JsonRedisSerializer<>(PresenceStatus.Presence.class); // 제네릭으로 타입 고정 > Presence 객체만 직렬화/역직렬화 가능

        // RedisTemplate에 적용
        tpl.setKeySerializer(keySer); // setKeySerializer: Redis key를 문자열로 저장
        tpl.setValueSerializer(valSer); // setValueSerializer: Redis value를 JSON으로 저장
        tpl.setHashKeySerializer(keySer); // setHashKeySerializer: Redis Hash 자료구조에도 동일하게 적용
        tpl.setHashValueSerializer(valSer); // setHashValueSerializer: Redis Hash 자료구조에도 동일하게 적용
        tpl.afterPropertiesSet(); // afterPropertiesSet(): Bean 초기화 후 적용 완료
        return tpl;
    }
}
