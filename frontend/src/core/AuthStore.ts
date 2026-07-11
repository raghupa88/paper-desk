import { UserInfo } from './types';

const TOKEN_KEY = 'paperdesk.token';
const USER_KEY = 'paperdesk.user';

/** Tiny localStorage-backed holder for the JWT and user info. */
export class AuthStore {
  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get user(): UserInfo | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserInfo) : null;
  }

  save(token: string, user: UserInfo) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}
