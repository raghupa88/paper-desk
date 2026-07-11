package com.paperdesk.sim;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Enums.SessionState;
import com.paperdesk.domain.Scenario;
import com.paperdesk.domain.ScenarioSession;
import com.paperdesk.repo.AccountRepo;
import com.paperdesk.repo.ScenarioRepo;
import com.paperdesk.repo.ScenarioSessionRepo;
import com.paperdesk.trading.InstrumentFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Session membership: joining a scenario finds-or-creates the shared default
 * session for it (cohorts get their own dedicated sessions) and opens the
 * user's trading account with the starting balance.
 */
@Service
public class SessionService {

    private final ScenarioRepo scenarioRepo;
    private final ScenarioSessionRepo sessionRepo;
    private final AccountRepo accountRepo;
    private final InstrumentFactory factory;
    private final SimEngine engine;
    private final SimProps props;

    public SessionService(ScenarioRepo scenarioRepo, ScenarioSessionRepo sessionRepo, AccountRepo accountRepo,
                          InstrumentFactory factory, SimEngine engine, SimProps props) {
        this.scenarioRepo = scenarioRepo;
        this.sessionRepo = sessionRepo;
        this.accountRepo = accountRepo;
        this.factory = factory;
        this.engine = engine;
        this.props = props;
    }

    @Transactional
    public Account join(long userId, long scenarioId) {
        ScenarioSession session = sessionRepo.findFirstByScenarioIdAndCohortIdIsNull(scenarioId)
                .orElseGet(() -> createSession(scenarioId, null));
        return openAccount(userId, session.id, props.startingBalance());
    }

    @Transactional
    public ScenarioSession createSession(long scenarioId, Long cohortId) {
        Scenario scenario = scenarioRepo.findById(scenarioId).orElseThrow();
        ScenarioSession session = new ScenarioSession();
        session.scenarioId = scenarioId;
        session.cohortId = cohortId;
        session.state = SessionState.RUNNING;
        session.simStart = Instant.now().truncatedTo(ChronoUnit.HOURS);
        session.currentSimTime = session.simStart;
        session.acceleration = scenario.acceleration;
        sessionRepo.save(session);
        LocalDate startDate = LocalDate.ofInstant(session.simStart, ZoneOffset.UTC);
        factory.createForSession(session.id, ScenarioParams.parse(scenario.paramsJson), startDate);
        engine.runtime(session.id); // register with the engine so it starts ticking
        return session;
    }

    @Transactional
    public Account openAccount(long userId, long sessionId, double startingBalance) {
        return accountRepo.findByUserIdAndSessionId(userId, sessionId).orElseGet(() -> {
            Account a = new Account();
            a.userId = userId;
            a.sessionId = sessionId;
            a.cashBalance = startingBalance;
            a.startingBalance = startingBalance;
            return accountRepo.save(a);
        });
    }
}
