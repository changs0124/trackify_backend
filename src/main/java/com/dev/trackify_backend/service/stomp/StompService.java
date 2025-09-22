package com.dev.trackify_backend.service.stomp;

import com.dev.trackify_backend.dto.request.stomp.ReqStompWorkingDto;
import com.dev.trackify_backend.dto.response.stomp.UserStateDto;

import com.dev.trackify_backend.dto.request.stomp.ReqStompPingDto;
import com.dev.trackify_backend.dto.request.stomp.ReqStompUserDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
import com.dev.trackify_backend.entity.User;
import com.dev.trackify_backend.event.UserLeaveEvent;
import com.dev.trackify_backend.status.PresenceStatus;
import com.dev.trackify_backend.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// StompController와 PresenceStatus 사이에서 "이벤트 라우팅 + 최소한의 검증/캐시"만 담당하는 어댑터 역할.
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

    @Transactional(readOnly = true)
    public void connect(ReqStompUserDto reqStompUserDto) {
        log.info("[CONNECT] {}", reqStompUserDto);

        // 유효 사용자 검증: 존재하지 않으면 즉시 예외 > 컨트롤러/핸들러에서 404 성격으로 매핑 가능.
        // readOnly 트랜잭션: 조회만 수행하여 불필요한 쓰기 락/flush를 피함.
        User tempUser = userMapper.findByUserCode(reqStompUserDto.getUserCode())
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        // PresenceService에 접속/위치 위임: 상태 엔진이 lastMsgAt, status(ONLINE) 등을 관리/브로드캐스트.
        presenceStatus.upsertOnConnect(
                tempUser.getUserCode(),
                tempUser.getUserName(),
                reqStompUserDto.getLat(),
                reqStompUserDto.getLng());
    }

    @Transactional(readOnly = true)
    public void update(ReqStompUserDto reqStompUserDto) {
        log.info("[MOVE] {}", reqStompUserDto);
        // 유효 사용자 검증: 잘못된 userCode로 맵/상태가 오염되는 것을 방지.
        userMapper.findByUserCode(reqStompUserDto.getUserCode())
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        presenceStatus.updateLocation(
                reqStompUserDto.getUserCode(),
                reqStompUserDto.getLat(),
                reqStompUserDto.getLng()
        );
    }

    public void working(ReqStompWorkingDto reqStompWorkingDto) {
        log.debug("[WORKING] {}", reqStompWorkingDto);
        presenceStatus.setWorking(reqStompWorkingDto.getUserCode(), reqStompWorkingDto.isWorking());
    }

    public void ping(ReqStompPingDto reqStompPingDto) {
        log.debug("[PING] {}", reqStompPingDto);
        presenceStatus.onPing(reqStompPingDto.getUserCode(), reqStompPingDto.getClientTime());
    }

    @Transactional
    public void disconnect(String userCode) {
        log.info("[DISCONNECT] {}", userCode);
        presenceStatus.disconnect(userCode, null, null);
    }

    @Transactional(readOnly = true)
    public List<RespStompUserDto> snapshot(String userCode) {
        var raw = presenceStatus.snapshot(userCode);

        return raw.stream()
                .filter(dto -> userMapper.existsByUserCode(dto.getUserCode()))
                .toList();
    }

    /** 떠남 이벤트 처리: 마지막 좌표 DB 저장 */
    @EventListener
    @Transactional
    public void onUserLeave(UserLeaveEvent e) {
        try {
            if (e.lat() != null && e.lng() != null) {
                userMapper.update(e.userCode(), e.lat(), e.lng());
            }
            log.info("[LEAVE:{}] {} ({}, {})", e.reason(), e.userCode(), e.lat(), e.lng());
        } catch (Exception ex) {
            log.warn("Failed to persist last location on LEAVE: {}", e, ex);
        }
    }
}
