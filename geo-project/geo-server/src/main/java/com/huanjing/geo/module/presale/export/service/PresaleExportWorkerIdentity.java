package com.huanjing.geo.module.presale.export.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

@Component
public class PresaleExportWorkerIdentity {
    private final String workerId = buildWorkerId();

    public String workerId() {
        return workerId;
    }

    private String buildWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        if (host.length() > 16) {
            host = host.substring(0, 16);
        }
        long pid = ProcessHandle.current().pid();
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return host + ":" + pid + ":" + uuid;
    }
}
