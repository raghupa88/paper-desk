// esp-js ships a UMD bundle that expects a browser-style global.
(globalThis as any).self = globalThis;
(globalThis as any).window = (globalThis as any).window ?? globalThis;

// Minimal localStorage + window.location polyfills for devtools activation tests
// (Node has no browser storage/location globals).
if (typeof (globalThis as any).localStorage === 'undefined') {
  class MemoryStorage {
    private store = new Map<string, string>();
    getItem(key: string) { return this.store.has(key) ? this.store.get(key)! : null; }
    setItem(key: string, value: string) { this.store.set(key, String(value)); }
    removeItem(key: string) { this.store.delete(key); }
    clear() { this.store.clear(); }
  }
  (globalThis as any).localStorage = new MemoryStorage();
}

if (typeof (globalThis as any).window.location === 'undefined') {
  (globalThis as any).window.location = { search: '' };
}
