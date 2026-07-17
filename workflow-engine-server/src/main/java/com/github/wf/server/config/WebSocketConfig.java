package com.github.wf.server.config;

import com.github.wf.server.ws.InstanceWebSocketHandler;
import com.github.wf.server.ws.MonitorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InstanceWebSocketHandler instanceHandler;
    private final MonitorWebSocketHandler monitorHandler;

    public WebSocketConfig(InstanceWebSocketHandler instanceHandler,
                           MonitorWebSocketHandler monitorHandler) {
        this.instanceHandler = instanceHandler;
        this.monitorHandler = monitorHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(instanceHandler, "/ws/instance/{instanceId}")
                .setAllowedOrigins("*");
        registry.addHandler(monitorHandler, "/ws/monitor")
                .setAllowedOrigins("*");
    }
}
