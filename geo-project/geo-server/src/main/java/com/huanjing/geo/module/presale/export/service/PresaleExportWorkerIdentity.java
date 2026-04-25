package com.huanjing.geo.module.presale.export.service;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
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
        long pid = ProcessHandle.current().pid();
        String runtime = ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
        return host + ":" + pid + ":" + runtime + ":" + UUID.randomUUID();
    }
}
