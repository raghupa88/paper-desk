import { Client, StompSubscription } from '@stomp/stompjs';
import { Router } from 'esp-js';
import { EventConst } from './events';

/**
 * Bridges the backend STOMP topics into esp Router events:
 *   /topic/clock/{sessionId}   -> clockTick (broadcast)
 *   /topic/prices/{sessionId}  -> pricesTick (broadcast)
 *   /topic/account/{accountId} -> accountEventReceived (broadcast)
 *
 * The JWT travels as a STOMP CONNECT header (connectHeaders), not an HTTP
 * header — a browser WebSocket handshake can't carry custom headers, so the
 * backend's StompAuthChannelInterceptor authenticates at the STOMP frame
 * layer instead and authorizes each SUBSCRIBE against topic ownership. No
 * token, no connection: the client only activates once a token is set, and
 * fully deactivates on logout rather than retrying forever unauthenticated.
 */
export class StompService {
  private client: Client | null = null;
  private subs: StompSubscription[] = [];
  private sessionId: number | null = null;
  private accountId: number | null = null;
  /** optional side-channel for services that need to react to account events */
  accountEventHook: ((ev: { type: string; detail?: unknown; code?: string }) => void) | null = null;

  constructor(private router: Router) {}

  /** Builds the client (inactive) so setToken() can activate it once a JWT exists. */
  start() {
    if (this.client) return;
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${proto}://${window.location.host}/ws`,
      reconnectDelay: 3000,
    });
    this.client.onConnect = () => this.resubscribe();
  }

  /** Call on login/signup/session-restore and on logout (with null) to keep the socket's auth in sync. */
  setToken(token: string | null) {
    if (!this.client) return;
    this.client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    if (token) {
      if (!this.client.active) this.client.activate();
    } else if (this.client.active) {
      this.clearSubs();
      void this.client.deactivate();
    }
  }

  stop() {
    this.clearSubs();
    void this.client?.deactivate();
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
