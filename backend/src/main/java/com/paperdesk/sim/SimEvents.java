package com.paperdesk.sim;

import java.time.LocalDate;
import java.util.Map;

/** Application events published by the SimEngine; listeners do matching and lifecycle work. */
public final class SimEvents {
    private SimEvents() {}

    /** Fired after a session's prices advanced on an engine tick. */
    public record SessionTicked(long sessionId) {}

    /**
     * Fired when a sim day completes. closes are the closing prices of closedDay,
     * floatingRate is the index level fixed at that close.
     */
    public record DayRolled(long sessionId, LocalDate closedDay, Map<String, Double> closes, double floatingRate) {}
}
