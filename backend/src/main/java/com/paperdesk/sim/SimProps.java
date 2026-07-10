package com.paperdesk.sim;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paperdesk.sim")
public record SimProps(long tickMillis, long gridStepSimSeconds, double defaultAcceleration,
                       double spreadBps, double startingBalance, boolean autoTick) {}
