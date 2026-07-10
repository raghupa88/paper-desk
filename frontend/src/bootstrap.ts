import { Router } from 'esp-js';
import 'esp-js-polimer'; // installs Router.modelBuilder()
import { Container } from 'esp-js-di';
import { ApiClient } from './core/ApiClient';
import { AuthStore } from './core/AuthStore';
import { DataService } from './core/DataService';
import { StompService } from './core/StompService';
import { EventConst } from './core/events';
import { registerSessionModel } from './models/SessionModel';
import { registerClockModel } from './models/ClockModel';
import { registerMarketModel } from './models/MarketModel';
import { registerTradingModel } from './models/TradingModel';
import { registerChainModel } from './models/ChainModel';
import { registerFxModel } from './models/FxModel';
import { registerInstructorModel } from './models/InstructorModel';
import { EspDevTools, installEspDevTools } from './devtools';

export interface AppServices {
  router: Router;
  container: Container;
  authStore: AuthStore;
  dataService: DataService;
  devTools: EspDevTools | null;
}

/** Composition root: router, devtools, DI container, services and polimer models. */
export function bootstrap(): AppServices {
  const router = new Router();

  // instrument the router before any model registers so the devtools sees them all
  const devTools = process.env.NODE_ENV === 'development'
    ? installEspDevTools(router, { maxEvents: 500 })
    : null;

  const container = new Container();
  container.registerInstance('router', router);
  container.registerFactory('authStore', () => new AuthStore()).singleton();
  container.registerFactory('apiClient', c => new ApiClient(
    c.resolve<AuthStore>('authStore'),
    () => router.broadcastEvent(EventConst.loggedOut, {}),
  )).singleton();
  container.registerFactory('stompService', c => new StompService(c.resolve<Router>('router'))).singleton();
  container.registerFactory('dataService', c => new DataService(
    c.resolve<ApiClient>('apiClient'),
    c.resolve<AuthStore>('authStore'),
    c.resolve<StompService>('stompService'),
    c.resolve<Router>('router'),
  )).singleton();

  const authStore = container.resolve<AuthStore>('authStore');
  const dataService = container.resolve<DataService>('dataService');
  const stomp = container.resolve<StompService>('stompService');

  registerSessionModel(router, authStore.user);
  registerClockModel(router);
  registerMarketModel(router);
  registerTradingModel(router);
  registerChainModel(router);
  registerFxModel(router);
  registerInstructorModel(router);

  stomp.start();
  if (authStore.token) {
    dataService.bootstrap().catch(() => dataService.logout());
  }

  return { router, container, authStore, dataService, devTools };
}
