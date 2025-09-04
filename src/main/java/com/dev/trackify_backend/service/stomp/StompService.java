package com.dev.trackify_backend.service.stomp;

import com.dev.trackify_backend.dto.response.stomp.UserStateDto;

import com.dev.trackify_backend.dto.request.stomp.ReqStompPingDto;
import com.dev.trackify_backend.dto.request.stomp.ReqStompUserDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
import com.dev.trackify_backend.entity.User;
import com.dev.trackify_backend.status.PresenceStatus;
import com.dev.trackify_backend.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

// StompController와 PresenceService 사이에서 "이벤트 라우팅 + 최소한의 검증/캐시"만 담당하는 어댑터 역할.
// 비즈니스 로직(상태 전이/브로드캐스트)은 PresenceService로 위임해 응집도를 높이고 중복 책임을 제거.
@Slf4j
@Service
@RequiredArgsConstructor
public class StompService {
    // 실시간 접속자 상태의 단일 소스(메모리 맵 + 스케줄러 + 브로커)
    // connect/move/ping/disconnect/setWorking 결과를 "상태 엔진"에 위임한다.
    @Autowired
    private PresenceStatus presenceStatus;

    // DB 접근 계층: userCode 유효성 체크 및 필요 시 마지막 좌표 저장 등에 사용.
    // (좌표 영속화는 disconnect에서 PresenceService가 담당하도록 일원화)
    @Autowired
    private UserMapper userMapper;

    // 사용자별 "마지막 좌표"를 보관하는 메모리 캐시.
    // ConcurrentHashMap을 쓰는 이유: 웹소켓 이벤트가 동시 다발적으로 들어와도 thread-safe.
    // HashMap은 동시성 이슈가 있으므로 부적절. ConcurrentHashMap은 버킷 단위 락으로 성능/안전성 균형.
    private final Map<String, UserStateDto> lastByUser = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public void connect(ReqStompUserDto reqStompUserDto) {
        log.info("[CONNECT] {}", reqStompUserDto);

        // 유효 사용자 검증: 존재하지 않으면 즉시 예외 > 컨트롤러/핸들러에서 404 성격으로 매핑 가능.
        // readOnly 트랜잭션: 조회만 수행하여 불필요한 쓰기 락/flush를 피함.
        User tempUser = userMapper.findByUserCode(reqStompUserDto.getUserCode())
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        // "마지막 좌표" 캐시에 반영: UI 초기 위치/종료 시 마지막 좌표 저장 등에 활용.
        lastByUser.put(
                tempUser.getUserCode(),
                new UserStateDto(tempUser.getUserCode(), reqStompUserDto.getLat(), reqStompUserDto.getLng())
        );

        // PresenceService에 접속/위치 위임: 상태 엔진이 lastMsgAt, status(ONLINE) 등을 관리/브로드캐스트.
        presenceStatus.upsertOnConnect(
                tempUser.getUserCode(),
                reqStompUserDto.getLat(),
                reqStompUserDto.getLng());
    }

    @Transactional(readOnly = true)
    public void update(ReqStompUserDto reqStompUserDto) {

        log.info("[MOVE] {}", reqStompUserDto);
        // 유효 사용자 검증: 잘못된 userCode로 맵/상태가 오염되는 것을 방지.
        userMapper.findByUserCode(reqStompUserDto.getUserCode())
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        // 캐시 갱신: 가장 최근 좌표를 메모리에 보관(종료 시 DB 영속화를 보다 정확히 하기 위함).
        lastByUser.put(
                reqStompUserDto.getUserCode(),
                new UserStateDto(reqStompUserDto.getUserCode(), reqStompUserDto.getLat(), reqStompUserDto.getLng())
        );

        // PresenceService에 위치 반영: lastMsgAt 갱신, 상태 계산(UNSTABLE/ONLINE 등)은 상태 엔진이 담당.
        presenceStatus.updateLocation(
                reqStompUserDto.getUserCode(),
                reqStompUserDto.getLat(),
                reqStompUserDto.getLng()
        );
    }

    public void ping(ReqStompPingDto reqStompPingDto) {
        log.debug("[PING] {}", reqStompPingDto);

        // 핑은 지연시간(RTT) 추정 및 lastMsgAt 갱신이 핵심 → PresenceService에 위임.
        // readOnly 트랜잭션 불필요(간단한 상태 갱신이며 DB 접근 없음).
        presenceStatus.onPing(reqStompPingDto.getUserCode(), reqStompPingDto.getClientTime());
    }

    @Transactional
    public void disconnect(String userCode) {
        log.info("[DISCONNECT] {}", userCode);

        // 캐시에서 마지막 좌표를 안전하게 제거하며 읽기:
        // disconnect는 "세션 종료의 단일 진입점"이므로 캐시 정리는 여기서 수행.
        // 스케줄러 OFFLINE 및 명시적 disconnect 간의 중복 제거를 위해 한쪽으로 일원화하는 게 중요.
        var cached = lastByUser.remove(userCode);
        Double lat = (cached != null) ? cached.getLat() : null;
        Double lng = (cached != null) ? cached.getLng() : null;

        // 최종 정리는 PresenceService로 위임(단일 출구):
        // presences 맵 제거
        // 좌표 영속화(DB 저장): 캐시 좌표가 우선, 없으면 Presence에 남은 좌표 사용
        // LEAVE 브로드캐스트
        // 이렇게 브로드캐스트/저장/정리를 한 곳에서 처리하면 레이스/중복 처리 리스크가 낮아진다.
        presenceStatus.disconnect(userCode, lat, lng);
    }

    public List<RespStompUserDto> snapshot() {
        return presenceStatus.snapshot().stream().map(RespStompUserDto::from).toList();
    }
}
