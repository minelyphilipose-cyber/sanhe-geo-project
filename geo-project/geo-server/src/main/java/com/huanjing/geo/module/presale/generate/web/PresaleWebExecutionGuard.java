package com.huanjing.geo.module.presale.generate.web;

@FunctionalInterface
public interface PresaleWebExecutionGuard {
    void checkActive() throws InterruptedException;
}
