package com.hakimi.aviation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置
 *
 * 内嵌 Tomcat 环境下，@ServerEndpoint 端点必须依赖此 Exporter 才会被扫描并发布，
 * 否则前端无法连接 ws://.../ws/notifications/{userId}，退款成功等通知也无法送达。
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
