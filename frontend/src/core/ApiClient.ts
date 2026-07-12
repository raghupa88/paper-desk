import { AuthStore } from './AuthStore';

/** Thin fetch wrapper: JSON in/out, JWT header, surfaced error messages. */
export class ApiClient {
  constructor(private auth: AuthStore, private onUnauthorized: () => void = () => {}) {}

  async get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  async put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PUT', path, body);
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    const token = this.auth.token;
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 401 || res.status === 403) {
      if (res.status === 401) this.onUnauthorized();
    }
    if (!res.ok) {
      let message = `${res.status} ${res.statusText}`;
      try {
        const data = await res.json();
        if (data && data.error) message = data.error;
      } catch {
        /* non-json error body */
      }
      throw new Error(message);
    }
    return (await res.json()) as T;
  }
}
