package com.huanjing.geo.module.dispatch.websearch.purge;

@FunctionalInterface
public interface PollPurgeOperation {
    String executeAndReturnAffectedRowsJson();
}
