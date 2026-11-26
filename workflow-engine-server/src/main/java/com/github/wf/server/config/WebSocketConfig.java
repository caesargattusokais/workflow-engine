package com.github.wf.server.config;

import com.github.wf.server.ws.InstanceWebSocketHandler;
import com.github.wf.server.ws.MonitorWebSocketHandler;
import com.github.wf.server.ws.WsAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InstanceWebSocketHandler instanceHandler;
    private final MonitorWebSocketHandler monitorHandler;
    private final WsAuthInterceptor authInterceptor;

    public WebSocketConfig(InstanceWebSocketHandler instanceHandler,
                           MonitorWebSocketHandler monitorHandler,
                           WsAuthInterceptor authInterceptor) {
        this.instanceHandler = instanceHandler;
        this.monitorHandler = monitorHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(instanceHandler, "/ws/instance/{instanceId}")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(monitorHandler, "/ws/monitor")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
    }
}
