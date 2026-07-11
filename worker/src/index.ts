// DRAFT — unverified against a real Cloudflare account. See docs/cd-setup-runbook.md.
//
// Routes /api/**, /ws, and /actuator/health into the backend Container;
// everything else (the webpack build's index.html and devtools.html, plus
// their hashed asset files) is served as static assets. Same-origin routing
// here is load-bearing: the frontend has no configurable API base URL, it
// assumes /api and /ws are reachable on its own origin (see
// frontend/src/core/ApiClient.ts and StompService.ts).
//
// The exact container-binding type (DurableObjectNamespace vs a dedicated
// Container binding) is one of the things the Phase 0 spike needs to
// confirm — this file assumes the fragmentary docs found during research
// were right that a Worker forwards into a container via a plain fetch()
// call on the binding, WebSocket Upgrade included.

export interface Env {
  ASSETS: Fetcher;
  BACKEND_CONTAINER: DurableObjectNamespace;
}

const BACKEND_PATHS = [/^\/api\//, /^\/ws$/, /^\/ws\//, /^\/actuator\/health$/];

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const isBackendPath = BACKEND_PATHS.some(pattern => pattern.test(url.pathname));

    if (isBackendPath) {
      const id = env.BACKEND_CONTAINER.idFromName('singleton');
      const stub = env.BACKEND_CONTAINER.get(id);
      return stub.fetch(request);
    }

    return env.ASSETS.fetch(request);
  },
};
