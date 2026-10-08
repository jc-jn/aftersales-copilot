package com.aftersales.copilot.common.observability;

import java.util.UUID;

public final class TraceIds {
    private TraceIds() {}

    public static String normalize(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,64}") ? value : UUID.randomUUID().toString();
    }
}
