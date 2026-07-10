package com.paperdesk.domain;

/** Namespace holder for the small enums used across the domain. */
public final class Enums {
    private Enums() {}

    public enum Role { STUDENT, INSTRUCTOR }

    public enum InstrumentType { EQUITY, FX_PAIR, OPTION, FX_OPTION, FUTURE, FORWARD, SWAP }

    public enum OrderSide { BUY, SELL }

    public enum OrderType { MARKET, LIMIT }

    public enum OrderStatus { NEW, FILLED, CANCELLED, REJECTED }

    public enum CallPut { CALL, PUT }

    public enum ViewContext { SALES, TRADER }

    public enum SessionState { RUNNING, PAUSED }

    public enum SettlementKind {
        FUTURES_MTM, MARGIN_CALL, LIQUIDATION,
        OPTION_EXERCISE, OPTION_ASSIGNMENT, OPTION_EXPIRY,
        FORWARD_SETTLE, SWAP_FIXING, SWAP_MATURITY
    }
}
