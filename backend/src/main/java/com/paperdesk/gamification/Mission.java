package com.paperdesk.gamification;

/**
 * The guided-mission catalog: bigger, narrative exercises that string
 * together several trades to teach a strategy or mechanic, as opposed to
 * badges which fire off single actions. Step text is shown as a checklist in
 * the Progress tab; MissionEvaluationService computes which steps are
 * currently satisfied.
 */
public enum Mission {
    FIRST_STEPS("First Steps", "Place your first trade and hold a position.", 50,
            "Place a trade that fills", "Hold at least one open position"),
    COVERED_CALL("Covered Call Writer", "Own at least 100 shares, then sell a call against them — " +
            "the classic income strategy.", 120,
            "Own 100+ shares of an equity", "Sell (short) a call option on that same equity"),
    PROTECTIVE_PUT("Protective Put", "Buy shares, then buy a put on the same stock as downside insurance.", 120,
            "Own shares of an equity", "Buy a put option on that same equity"),
    LONG_STRADDLE("Long Straddle", "Buy a call and a put at the same strike and expiry — " +
            "a bet on a big move, either direction.", 130,
            "Buy a call option", "Buy a put with the same underlying, strike and expiry as that call"),
    FUTURES_LAB("Futures Settlement Lab", "Open a futures position and hold it through a daily mark-to-market.", 100,
            "Trade a futures contract", "Hold it through at least one daily settlement"),
    FX_DESK("FX Desk Rotation", "Work both sides of the FX desk: trade as Trader, then quote as Sales.", 130,
            "Trade an FX product from the Trader desk", "Execute a client RFQ from the Sales desk"),
    SWAP_LAB("Swap Lab", "Enter an interest-rate swap and collect a fixing.", 110,
            "Trade an interest-rate swap", "Receive at least one swap fixing cash flow");

    public final String title;
    public final String description;
    public final int xp;
    public final String[] steps;

    Mission(String title, String description, int xp, String... steps) {
        this.title = title;
        this.description = description;
        this.xp = xp;
        this.steps = steps;
    }
}
