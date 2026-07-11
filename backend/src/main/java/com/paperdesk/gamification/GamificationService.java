package com.paperdesk.gamification;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Achievement;
import com.paperdesk.domain.Enums.InstrumentType;
import com.paperdesk.domain.Enums.OrderType;
import com.paperdesk.domain.EquitySnapshot;
import com.paperdesk.domain.Instrument;
import com.paperdesk.domain.TradeOrder;
import com.paperdesk.repo.AchievementRepo;
import com.paperdesk.repo.EquitySnapshotRepo;
import com.paperdesk.sim.ScenarioParams;
import com.paperdesk.sim.SessionRuntime;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Awards XP and one-time badges. Callers pass the Account entity they are
 * already mutating/saving so XP updates ride the same persistence, and the
 * unlock is pushed to the student's WebSocket topic for an instant toast.
 */
@Service
public class GamificationService {

    public static final double XP_PER_FILL = 5;

    private static final Set<String> TYPE_BADGES = Set.of(
            Badge.STOCK_PICKER.name(), Badge.OPTIONS_APPRENTICE.name(), Badge.FUTURES_PIONEER.name(),
            Badge.FX_DEALER.name(), Badge.FORWARD_THINKER.name(), Badge.SWAP_STARTER.name());

    private final AchievementRepo achievementRepo;
    private final EquitySnapshotRepo snapshotRepo;
    private final SimpMessagingTemplate ws;

    public GamificationService(AchievementRepo achievementRepo, EquitySnapshotRepo snapshotRepo,
                               SimpMessagingTemplate ws) {
        this.achievementRepo = achievementRepo;
        this.snapshotRepo = snapshotRepo;
        this.ws = ws;
    }

    /** Called on every fill, with the same Account instance the order flow persists. */
    public void onFill(Account account, Instrument instr, TradeOrder order, LocalDate simDate) {
        account.xp += XP_PER_FILL;
        unlock(account, Badge.FIRST_TRADE, simDate);
        Badge typeBadge = switch (instr.instrumentType) {
            case EQUITY -> Badge.STOCK_PICKER;
            case OPTION -> Badge.OPTIONS_APPRENTICE;
            case FUTURE -> Badge.FUTURES_PIONEER;
            case FX_PAIR, FX_OPTION -> Badge.FX_DEALER;
            case FORWARD -> Badge.FORWARD_THINKER;
            case SWAP -> Badge.SWAP_STARTER;
        };
        unlock(account, typeBadge, simDate);
        if (order.orderType == OrderType.LIMIT) unlock(account, Badge.LIMIT_TACTICIAN, simDate);
        if (achievementRepo.countByAccountIdAndCodeIn(account.id, TYPE_BADGES) >= 4) {
            unlock(account, Badge.WELL_DIVERSIFIED, simDate);
        }
    }

    /** Futures margin call — the badge reframes a painful moment as a lesson. */
    public void onMarginCall(Account account, LocalDate simDate) {
        unlock(account, Badge.MARGIN_CALL_LESSON, simDate);
    }

    /** Long option settled: worthless (theta lesson) or ITM auto-exercise. */
    public void onOptionSettled(Account account, boolean inTheMoney, double qty, LocalDate simDate) {
        if (qty <= 0) return; // writers get assignment cash flows, not these badges
        unlock(account, inTheMoney ? Badge.IN_THE_MONEY : Badge.THETA_TUITION, simDate);
    }

    /** Called after the end-of-day equity snapshot is written. */
    public void onDayClosed(Account account, LocalDate day, double equity, SessionRuntime rt) {
        List<EquitySnapshot> recent = snapshotRepo.findByAccountIdOrderBySimDate(account.id);
        int n = recent.size();
        double previous = n >= 2 ? recent.get(n - 2).equity : account.startingBalance;
        if (equity > previous) {
            unlock(account, Badge.PROFITABLE_DAY, day);
            int streak = 1;
            for (int i = n - 2; i >= 1 && streak < 5; i--) {
                if (recent.get(i).equity > recent.get(i - 1).equity) streak++;
                else break;
            }
            if (n >= 2 && streak >= 5) unlock(account, Badge.HOT_STREAK_5, day);
        }
        ScenarioParams.Crash crash = rt.params.crash();
        if (crash != null && equity >= account.startingBalance) {
            long dayIndex = ChronoUnit.DAYS.between(LocalDate.ofInstant(rt.simStart, ZoneOffset.UTC), day);
            if (dayIndex >= crash.day() + 3) unlock(account, Badge.CRASH_SURVIVOR, day);
        }
    }

    /** Idempotent one-time unlock: persists, adds XP to the passed entity, pushes a toast. */
    public void unlock(Account account, Badge badge, LocalDate simDate) {
        if (achievementRepo.existsByAccountIdAndCode(account.id, badge.name())) return;
        Achievement a = new Achievement();
        a.accountId = account.id;
        a.code = badge.name();
        a.xp = badge.xp;
        a.simDate = simDate;
        achievementRepo.save(a);
        account.xp += badge.xp;

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "ACHIEVEMENT");
        msg.put("detail", badge.title + " (+" + badge.xp + " XP) — " + badge.description);
        msg.put("code", badge.name());
        ws.convertAndSend("/topic/account/" + account.id, msg);
    }
}
