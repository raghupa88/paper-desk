import { Router, observeEvent } from 'esp-js';
import { ImmutableModel } from 'esp-js-polimer';
import { EventConst, ModelIds } from '../core/events';
import { AccountInfo, Scenario, UserInfo } from '../core/types';

export interface SessionState {
  user: UserInfo | null;
  scenarios: Scenario[];
  accounts: AccountInfo[];
  activeAccount: AccountInfo | null;
  joiningScenarioId: number | null;
}

export interface SessionModel extends ImmutableModel {
  state: SessionState;
}

class SessionStateHandlers {
  @observeEvent(EventConst.loggedIn)
  onLoggedIn(draft: SessionState, user: UserInfo) {
    draft.user = user;
  }

  @observeEvent(EventConst.loggedOut)
  onLoggedOut(draft: SessionState) {
    draft.user = null;
    draft.accounts = [];
    draft.activeAccount = null;
  }

  @observeEvent(EventConst.scenariosLoaded)
  onScenarios(draft: SessionState, scenarios: Scenario[]) {
    draft.scenarios = scenarios;
  }

  @observeEvent(EventConst.accountsLoaded)
  onAccounts(draft: SessionState, accounts: AccountInfo[]) {
    draft.accounts = accounts;
  }

  @observeEvent(EventConst.joinScenarioRequested)
  onJoinRequested(draft: SessionState, ev: { scenarioId: number }) {
    draft.joiningScenarioId = ev.scenarioId;
  }

  @observeEvent(EventConst.accountSelected)
  onAccountSelected(draft: SessionState, account: AccountInfo) {
    draft.activeAccount = account;
    draft.joiningScenarioId = null;
  }
}

export function registerSessionModel(router: Router, initialUser: UserInfo | null) {
  return router.modelBuilder!<SessionModel>()
    .withInitialModel({
      modelId: ModelIds.session,
      state: { user: initialUser, scenarios: [], accounts: [], activeAccount: null, joiningScenarioId: null },
    })
    .withStateHandlers('state', new SessionStateHandlers())
    .registerWithRouter();
}
