package com.hakimi.aviation.component.notification;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
// 访问路径携带 userId，比如 ws://127.0.0.1:8080/ws/notifications/10086
@ServerEndpoint("/ws/notifications/{userId}")
public class NotificationWebSocketServer {

    // 存放所有在线用户的 Session
    // TODO 用内存存储用户 Session 的方案后续需要优化迭代，此处仅作 第一版 MVP 的执行方案
    private static final ConcurrentHashMap<Long, Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        SESSION_MAP.put(userId, session);
        log.info("用户 {} 建立连接，当前在线人数: {}", userId, SESSION_MAP.size());
    }

    @OnClose
    public void onClose(@PathParam("userId") Long userId) {
        SESSION_MAP.remove(userId);
        log.info("用户 {} 断开连接，当前在线人数: {}", userId, SESSION_MAP.size());
    }

    /**
     * 核心推送方法：根据 userId 精准推送
     */
    public void sendMessage(Long userId, String message) {
        Session session = SESSION_MAP.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 异步发送文本消息
                session.getAsyncRemote().sendText(message);
            } catch (Exception e) {
                log.error("给用户 {} 推送消息失败", userId, e);
            }
        }
    }
}
