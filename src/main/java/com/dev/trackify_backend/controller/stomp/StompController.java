package com.dev.trackify_backend.controller.stomp;

import com.dev.trackify_backend.dto.request.stomp.ReqStompPingDto;
import com.dev.trackify_backend.dto.request.stomp.ReqStompUserDto;
import com.dev.trackify_backend.dto.request.stomp.ReqStompWorkingDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
import com.dev.trackify_backend.service.stomp.StompService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StompController {

    @Autowired
    private StompService stompService;

    @MessageMapping("/connect")
    public void connect(@Payload ReqStompUserDto ReqStompUserDto) {
        log.info("{}", ReqStompUserDto);
        stompService.connect(ReqStompUserDto);
    }

    @MessageMapping("/update")
    public void update(@Payload ReqStompUserDto reqStompUserDto) {
        log.info("{}", reqStompUserDto);
        stompService.update(reqStompUserDto);
    }

    @MessageMapping("/working")
    public void setWorking(ReqStompWorkingDto reqStompWorkingDto) {
        log.info("{}", reqStompWorkingDto);
        stompService.working(reqStompWorkingDto);
    }

    @MessageMapping("/ping")
    public void ping(@Payload ReqStompPingDto reqStompPingDto) {
        stompService.ping(reqStompPingDto);
    }

    @MessageMapping("/disconnect")
    public void disconnect(@Payload String userCode) {
        log.info("{}", userCode);
        stompService.disconnect(userCode);
    }

    @MessageMapping("/presence/snapshot")
    @SendToUser("/queue/presence")
    public List<RespStompUserDto> snapshot(@Payload ReqStompPingDto reqStompPingDto) {
        return stompService.snapshot(reqStompPingDto.getUserCode());
    }
}
