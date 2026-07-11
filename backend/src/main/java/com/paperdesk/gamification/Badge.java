package com.paperdesk.gamification;

/**
 * The achievement catalog. Every badge is a one-time unlock worth XP, tied to
 * a teaching moment — the goal is to nudge students through the whole
 * curriculum (each instrument type, order types, risk lessons), not just to
 * reward profit.
 */
public enum Badge {
    FIRST_TRADE("First Trade", "Placed your first filled order.", 50),
    STOCK_PICKER("Stock Picker", "Traded your first equity.", 50),
    OPTIONS_APPRENTICE("Options Apprentice", "Traded your first option — premiums, strikes and Greeks unlocked.", 75),
    FUTURES_PIONEER("Futures Pioneer", "Opened your first futures position and posted initial margin.", 75),
    FX_DEALER("FX Dealer", "Dealt your first FX product.", 75),
    FORWARD_THINKER("Forward Thinker", "Entered your first forward — like a future, but settled once at maturity.", 75),
    SWAP_STARTER("Swap Starter", "Entered an interest-rate swap — welcome to the OTC world.", 100),
    LIMIT_TACTICIAN("Limit Tactician", "Got a resting limit order filled at your price.", 60),
    WELL_DIVERSIFIED("Well Diversified", "Traded four or more different instrument types.", 150),
    PROFITABLE_DAY("Green Day", "Closed a sim day with your equity up.", 80),
    HOT_STREAK_5("Hot Streak", "Five profitable sim days in a row.", 200),
    MARGIN_CALL_LESSON("Margin Call Survivor", "Received a margin call — an expensive lesson in leverage.", 40),
    THETA_TUITION("Theta Tuition", "Held a long option to worthless expiry. Time decay is real.", 40),
    IN_THE_MONEY("In The Money", "An option you held expired in the money and auto-exercised.", 90),
    CRASH_SURVIVOR("Crash Survivor", "Kept your equity above the starting balance through a market crash.", 250);

    public final String title;
    public final String description;
    public final int xp;

    Badge(String title, String description, int xp) {
        this.title = title;
        this.description = description;
        this.xp = xp;
    }
}
