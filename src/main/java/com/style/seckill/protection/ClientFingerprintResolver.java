package com.style.seckill.protection;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientFingerprintResolver {

    public String resolve(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId != null && !clientId.isBlank()) {
            return clientId.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr;
        }
        return "unknown-client";
    }
}
