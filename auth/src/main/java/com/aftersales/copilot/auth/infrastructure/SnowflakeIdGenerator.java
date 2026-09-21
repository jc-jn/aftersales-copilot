package com.aftersales.copilot.auth.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    private static final long CUSTOM_EPOCH = 1_704_067_200_000L;
    private static final long SEQUENCE_MASK = 0xFFFL;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(@Value("${app.instance-id:0}") long nodeId) {
        if (nodeId < 0 || nodeId > 1023) {
            throw new IllegalArgumentException("app.instance-id must be between 0 and 1023");
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("System clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitForNextMillis(timestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - CUSTOM_EPOCH) << 22) | (nodeId << 12) | sequence;
    }

    private long waitForNextMillis(long timestamp) {
        long next = timestamp;
        while (next <= timestamp) {
            Thread.onSpinWait();
            next = System.currentTimeMillis();
        }
        return next;
    }
}
