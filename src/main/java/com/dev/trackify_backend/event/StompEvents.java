package com.dev.trackify_backend.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompEvents {
    @EventListener
    public void onConnect(SessionConnectEvent e) {
        System.out.println("STOMP CONNECT frame received");
    }

    @EventListener
    public void onConnected(SessionConnectedEvent e) {
        System.out.println("STOMP CONNECTED ✅");
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        System.out.println("WS DISCONNECT " + e.getCloseStatus());
    }
}
