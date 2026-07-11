package com.paperdesk.sim;

import com.paperdesk.domain.DailyBar;
import com.paperdesk.domain.Enums.SessionState;
import com.paperdesk.domain.Scenario;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.repo.DailyBarRepo;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.repo.ScenarioSessionRepo;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives every loaded scenario session: advances sim time on a real-time tick,
 * steps GBM prices on a fixed sim-time grid (deterministic per seed), tracks
 * daily bars, and fires DayRolled/SessionTicked events for settlement,
 * expiry and order-matching listeners.
 */
@Service
public class SimEngine {

    private static final double SECONDS_PER_YEAR = 365.0 * 86400.0;

    private final Map<Long, SessionRuntime> runtimes = new ConcurrentHashMap<>();
    private final ScenarioRepo scenarioRepo;
    private final ScenarioSessionRepo sessionRepo;
    private final DailyBarRepo barRepo;
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate ws;
    private final SimProps props;
    private long tickCount;

    public SimEngine(ScenarioRepo scenarioRepo, ScenarioSessionRepo sessionRepo, DailyBarRepo barRepo,
                     ApplicationEventPublisher events, SimpMessagingTemplate ws, SimProps props) {
        this.scenarioRepo = scenarioRepo;
        this.sessionRepo = sessionRepo;
        this.barRepo = barRepo;
        this.events = events;
        this.ws = ws;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadExistingSessions() {
        sessionRepo.findAll().forEach(s -> runtime(s.id));
    }

    /** Lazily builds the runtime, replaying the deterministic path up to the persisted sim time. */
    public SessionRuntime runtime(long sessionId) {
        return runtimes.computeIfAbsent(sessionId, id -> {
            ScenarioSession sess = sessionRepo.findById(id).orElseThrow();
            Scenario sc = scenarioRepo.findById(sess.scenarioId).orElseThrow();
            SessionRuntime rt = new SessionRuntime(id, sc.seed, ScenarioParams.parse(sc.paramsJson),
                    sess.acceleration, sess.simStart);
            rt.paused = sess.state == SessionState.PAUSED;
            advanceTo(rt, sess.currentSimTime, false);
            return rt;
        });
    }

    @Scheduled(fixedRateString = "${paperdesk.sim.tick-millis}")
    public void tick() {
        if (!props.autoTick()) return;
        tickCount++;
        double tickSeconds = props.tickMillis() / 1000.0;
        for (SessionRuntime rt : runtimes.values()) {
            boolean advanced = false;
            synchronized (rt) {
                if (!rt.paused) {
                    Instant target = rt.simTime.plusMillis((long) (rt.acceleration * tickSeconds * 1000));
                    advanceTo(rt, target, true);
                    advanced = true;
                }
            }
            broadcast(rt);
            if (advanced) events.publishEvent(new SimEvents.SessionTicked(rt.sessionId));
            if (tickCount % 30 == 0) persistClock(rt);
        }
    }

    /** Instantly advances one sim day (running every grid step and the day roll). Caller-facing clock control. */
    public void stepOneDay(long sessionId) {
        SessionRuntime rt = runtime(sessionId);
        synchronized (rt) {
            advanceTo(rt, rt.simTime.plus(1, ChronoUnit.DAYS), true);
        }
        events.publishEvent(new SimEvents.SessionTicked(sessionId));
        persistClock(rt);
        broadcast(rt);
    }

    public void setPaused(long sessionId, boolean paused) {
        SessionRuntime rt = runtime(sessionId);
        rt.paused = paused;
        persistClock(rt);
        broadcast(rt);
    }

    public void setAcceleration(long sessionId, double acceleration) {
        SessionRuntime rt = runtime(sessionId);
        rt.acceleration = Math.max(1, Math.min(86400, acceleration));
        persistClock(rt);
    }

    // ---- core advance loop ----

    private void advanceTo(SessionRuntime rt, Instant target, boolean fireEvents) {
        if (target.isBefore(rt.simTime)) return;
        long stepSec = props.gridStepSimSeconds();
        while (true) {
            Instant nextGrid = rt.simStart.plusSeconds((rt.gridStep + 1) * stepSec);
            if (nextGrid.isAfter(target)) break;
            rt.gridStep++;
            rt.simTime = nextGrid;
            LocalDate day = LocalDate.ofInstant(nextGrid, ZoneOffset.UTC);
            if (!day.equals(rt.currentDay)) {
                rollDay(rt, rt.currentDay, day, fireEvents);
            }
            stepPrices(rt, stepSec);
        }
        if (target.isAfter(rt.simTime)) rt.simTime = target;
    }

    private void stepPrices(SessionRuntime rt, long stepSec) {
        double dt = stepSec / SECONDS_PER_YEAR;
        double sqrtDt = Math.sqrt(dt);
        for (SessionRuntime.SymbolState st : rt.symbols.values()) {
            double vol = st.spec.vol() * rt.volMult;
            double drift = st.spec.drift();
            if (drift == 0 && "FX_PAIR".equals(st.spec.type())) {
                drift = st.spec.domesticRate() - st.spec.foreignRate(); // covered interest parity
            }
            double z = st.rng.nextGaussian();
            st.price *= Math.exp((drift - vol * vol / 2) * dt + vol * sqrtDt * z);
            rt.dayBars.computeIfAbsent(st.spec.symbol(), sym -> new SessionRuntime.Bar(st.price))
                    .update(st.price);
        }
    }

    private void rollDay(SessionRuntime rt, LocalDate closedDay, LocalDate newDay, boolean fireEvents) {
        Map<String, Double> closes = rt.currentPrices();
        if (fireEvents) {
            persistBars(rt, closedDay);
            events.publishEvent(new SimEvents.DayRolled(rt.sessionId, closedDay, closes, rt.floatingRate));
        }
        rt.currentDay = newDay;
        rt.dayBars.clear();

        long dayIndex = ChronoUnit.DAYS.between(LocalDate.ofInstant(rt.simStart, ZoneOffset.UTC), newDay);
        ScenarioParams.Crash crash = rt.params.crash();
        if (crash != null && dayIndex == crash.day()) {
            rt.symbols.values().forEach(s -> s.price *= (1 + crash.jump()));
            rt.volMult = crash.volMult();
        }
        ScenarioParams.Floating f = rt.params.floating();
        if (f != null) {
            double z = rt.ratesRng.nextGaussian();
            rt.floatingRate = Math.max(0.0005,
                    rt.floatingRate + f.meanRev() * (f.mean() - rt.floatingRate) + f.vol() * Math.sqrt(1 / 252.0) * z);
        }
    }

    private void persistBars(SessionRuntime rt, LocalDate day) {
        rt.dayBars.forEach((symbol, bar) -> {
            if (barRepo.existsBySessionIdAndSymbolAndSimDate(rt.sessionId, symbol, day)) return;
            DailyBar b = new DailyBar();
            b.sessionId = rt.sessionId;
            b.symbol = symbol;
            b.simDate = day;
            b.open = bar.open;
            b.high = bar.high;
            b.low = bar.low;
            b.close = bar.close;
            barRepo.save(b);
        });
    }

    private void persistClock(SessionRuntime rt) {
        sessionRepo.findById(rt.sessionId).ifPresent(s -> {
            s.currentSimTime = rt.simTime;
            s.acceleration = rt.acceleration;
            s.state = rt.paused ? SessionState.PAUSED : SessionState.RUNNING;
            sessionRepo.save(s);
        });
    }

    private void broadcast(SessionRuntime rt) {
        Map<String, Object> clock = new LinkedHashMap<>();
        clock.put("sessionId", rt.sessionId);
        clock.put("simTime", rt.simTime.toString());
        clock.put("simDate", rt.simDate().toString());
        clock.put("paused", rt.paused);
        clock.put("acceleration", rt.acceleration);
        clock.put("floatingRate", rt.floatingRate);
        ws.convertAndSend("/topic/clock/" + rt.sessionId, clock);

        Map<String, Object> prices = new LinkedHashMap<>();
        prices.put("simTime", rt.simTime.toString());
        prices.put("prices", rt.currentPrices());
        ws.convertAndSend("/topic/prices/" + rt.sessionId, prices);
    }
}
