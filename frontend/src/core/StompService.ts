import { Client, StompSubscription } from '@stomp/stompjs';
import { Router } from 'esp-js';
import { EventConst } from './events';

/**
 * Bridges the backend STOMP topics into esp Router events:
 *   /topic/clock/{sessionId}   -> clockTick (broadcast)
 *   /topic/prices/{sessionId}  -> pricesTick (broadcast)
 *   /topic/account/{accountId} -> accountEventReceived (broadcast)
 */
export class StompService {
  private client: Client | null = null;
  private subs: StompSubscription[] = [];
  private sessionId: number | null = null;
  private accountId: number | null = null;
  /** optional side-channel for services that need to react to account events */
  accountEventHook: ((ev: { type: string; detail?: unknown; code?: string }) => void) | null = null;

  constructor(private router: Router) {}

  start() {
    if (this.client) return;
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${proto}://${window.location.host}/ws`,
      reconnectDelay: 3000,
    });
    this.client.onConnect = () => this.resubscribe();
    this.client.activate();
  }

  stop() {
    this.clearSubs();
    this.client?.deactivate();
    this.client = null;
  }

  /** Point the live subscriptions at the active session/account. */
  setContext(sessionId: number | null, accountId: number | null) {
    this.sessionId = sessionId;
    this.accountId = accountId;
    if (this.client?.connected) this.resubscribe();
  }

  private resubscribe() {
    this.clearSubs();
    if (!this.client?.connected) return;
    if (this.sessionId != null) {
      this.subs.push(this.client.subscribe(`/topic/clock/${this.sessionId}`, msg =>
        this.router.broadcastEvent(EventConst.clockTick, JSON.parse(msg.body))));
      this.subs.push(this.client.subscribe(`/topic/prices/${this.sessionId}`, msg =>
        this.router.broadcastEvent(EventConst.pricesTick, JSON.parse(msg.body))));
    }
    if (this.accountId != null) {
      this.subs.push(this.client.subscribe(`/topic/account/${this.accountId}`, msg => {
        const ev = JSON.parse(msg.body);
        this.router.broadcastEvent(EventConst.accountEventReceived, ev);
        this.accountEventHook?.(ev);
      }));
    }
  }

  private clearSubs() {
    this.subs.forEach(s => {
      try { s.unsubscribe(); } catch { /* connection may be gone */ }
    });
    this.subs = [];
  }
}
