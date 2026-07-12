# Integrating the esp-js DevTools into another project

This is a step-by-step guide to porting Paper Desk's esp-js DevTools —
a Redux-DevTools-style event tracer for `esp-js` apps — into a **different**
React + esp-js project. The implementation here has no dependency on any
Paper Desk application code (models, events, domain types), so it copies
over as a self-contained module. See `README.md`'s "esp-js DevTools" section
and `docs/architecture.md`'s DevTools subsection for the design rationale;
this doc is the "how do I get this into another repo" checklist.

## What you're copying

A **recorder** that instruments an esp-js `Router` (patches
`publishEvent`/`broadcastEvent`/`addModel` to log every event + resulting
model state), and a **standalone viewer** that runs as a second HTML page /
browser tab, not an in-page overlay. The two talk over a same-origin
`BroadcastChannel` — no server, no extension packaging, and the viewer
survives the app tab crashing or reloading. Optionally it also bridges into
the Redux DevTools browser extension if the user has it installed.

Nine files, all under `frontend/src/devtools/`:

```
devtools/
├── index.ts                    # barrel — only the activation API, not the recorder itself
├── protocol.ts                 # wire format shared by recorder + panel
├── espDevTools.ts              # the recorder (patches the Router)
├── activation.ts               # when/how the recorder turns on; opens the panel window
├── DevToolsLauncher.tsx        # small always-mounted button component
├── __tests__/
│   ├── espDevTools.test.ts
│   └── activation.test.ts
└── panel/
    ├── panel.html              # standalone HTML document for the viewer
    ├── panelEntry.tsx          # viewer's own React root — zero imports from the host app
    └── PanelApp.tsx            # the viewer UI itself
```

Nothing in these files imports application models, event types, or the DI
container. The only two things you need to supply are (1) your project's
`esp-js` `Router` instance and (2) — optionally — an `esp-js-polimer` model
if you want state snapshots (plain esp-js models without polimer still get
traced, just without the `getImmutableModel()` unwrap).

## Prerequisites

- `esp-js` and `esp-js-react` already in the target project (`esp-js-di`
  and `esp-js-polimer` are optional — the recorder degrades gracefully
  without polimer, see [Adapting to non-polimer state](#adapting-to-non-polimer-state)).
- React 18+ (uses `react-dom/client`'s `createRoot`).
- A bundler that supports **code-splitting via dynamic `import()`** and
  **multiple HTML entry points** — webpack (this repo), Vite, or similar.
  Both are load-bearing: dynamic `import()` is what keeps the recorder out
  of the production bundle until activated, and a second HTML entry is what
  lets the panel be a genuinely separate browsing context instead of an
  in-page overlay. See [Adapting the bundler config](#adapting-the-bundler-config-webpack-vs-vite)
  if the target project uses Vite instead of webpack.

## Step 1 — copy the files

Copy `frontend/src/devtools/` verbatim into the target project (e.g. to
`src/devtools/`). Nothing inside needs edits *except* the two
project-specific identifiers called out in Step 2.

## Step 2 — rename the project-specific identifiers

Three strings in the copied files are Paper-Desk-specific and should become
your new project's name so multiple esp-js apps on the same origin (or the
same browser, different origins) don't collide:

| File | Identifier | Current value | Purpose |
|---|---|---|---|
| `protocol.ts` | `DEVTOOLS_CHANNEL` | `'paperdesk-esp-devtools'` | `BroadcastChannel` name — recorder and panel must agree, and it should be unique per app since `BroadcastChannel` is scoped to the browser origin |
| `activation.ts` | `STORAGE_KEY` | `'paperdesk.devtoolsEnabled'` | `localStorage` key that persists the opt-in |
| `activation.ts` | `PANEL_WINDOW_NAME` | `'paperdesk-esp-devtools'` | `window.open` target name (reuses the same popup instead of spawning duplicates) |

```ts
// protocol.ts
export const DEVTOOLS_CHANNEL = 'yourapp-esp-devtools';

// activation.ts
const STORAGE_KEY = 'yourapp.devtoolsEnabled';
const PANEL_WINDOW_NAME = 'yourapp-esp-devtools';
```

Also update the two `<title>` strings and the header text in `panel.html` /
`PanelApp.tsx` if you want the viewer window to say your app's name instead
of "Paper Desk — esp DevTools" — cosmetic only, safe to skip.

## Step 3 — wire the bundler's second entry point

The panel needs its own HTML document and its own JS bundle, served
alongside the main app's `index.html`.

### webpack

```js
// webpack.config.js
module.exports = (env, argv) => {
  const isDev = argv.mode !== 'production';
  return {
    entry: {
      main: './src/index.tsx',
      devtools: './src/devtools/panel/panelEntry.tsx',   // add this
    },
    // ...
    plugins: [
      new HtmlWebpackPlugin({
        template: './src/index.html',
        filename: 'index.html',
        chunks: ['main'],
      }),
      new HtmlWebpackPlugin({                              // add this block
        template: './src/devtools/panel/panel.html',
        filename: 'devtools.html',
        chunks: ['devtools'],
      }),
      new webpack.DefinePlugin({
        'process.env.NODE_ENV': JSON.stringify(isDev ? 'development' : 'production'),
      }),
    ],
    devServer: {
      // devtools.html must be served as itself, not swallowed by the SPA fallback
      historyApiFallback: {
        rewrites: [
          { from: /^\/devtools\.html$/, to: '/devtools.html' },
          { from: /./, to: '/index.html' },
        ],
      },
      // only needed if your dev-server's own HMR socket path collides with
      // an app route — Paper Desk's app uses /ws for STOMP, so its dev
      // server socket is moved to avoid the clash; skip this if you don't
      // have that collision
      // webSocketServer: { options: { path: '/__wds_hmr' } },
    },
  };
};
```

If `process.env.NODE_ENV` isn't already defined by your webpack setup (it
usually is via `mode`), the `DefinePlugin` block above is required —
`activation.ts` reads it directly to decide dev-vs-prod behavior.

### Adapting the bundler config: webpack vs Vite

Vite's equivalent is `build.rollupOptions.input` (multiple HTML entries)
plus `server.` no special rewrite needed, since Vite serves any file under
`root` matching its actual path by default — you'd just make sure
`devtools.html` sits alongside `index.html` and is *not* caught by an SPA
`historyApiFallback` middleware if you've added one manually:

```ts
// vite.config.ts
export default defineConfig({
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        devtools: resolve(__dirname, 'src/devtools/panel/panel.html'),
      },
    },
  },
});
```

`import.meta.env.DEV` is Vite's equivalent of
`process.env.NODE_ENV === 'development'` — update the one check in
`activation.ts` (`installDevToolsActivation`) and the one in
`DevToolsLauncher.tsx` accordingly if you port to Vite. Dynamic `import()`
code-splitting and `panelEntry.tsx`'s standalone React root need no changes
either way.

## Step 4 — instrument the router at your composition root

Call `installDevToolsActivation` as early as possible — before any model
registers — so the backfill buffer captures everything from app startup,
not just events after some later point:

```ts
// bootstrap.ts (or wherever your Router is constructed)
import { Router } from 'esp-js';
import { installDevToolsActivation } from './devtools';

export function bootstrap() {
  const router = new Router();

  installDevToolsActivation(router, { backfillSize: 200 });

  // ...construct DI container, register models, etc.
  return { router /* ... */ };
}
```

This call is cheap in every mode: in dev it dynamically imports the ~3&nbsp;KB
recorder chunk immediately; in production it does nothing until the user
opts in (see [Activation policy](#activation-policy-recap)), so it adds no
meaningful weight to a default production load.

## Step 5 — mount the launcher button

`DevToolsLauncher` needs to run inside your `esp-js-react` Router context
(it calls `useRouter()`), so mount it anywhere under whatever provider
supplies that context — typically your root `<App>`:

```tsx
// App.tsx
import { DevToolsLauncher } from './devtools/DevToolsLauncher';

export function App() {
  return (
    <RouterProvider router={router /* however your project supplies this */}>
      {/* ...rest of the app... */}
      <DevToolsLauncher />
    </RouterProvider>
  );
}
```

It renders a small fixed-position badge (bottom-right, semi-transparent
until hovered) that opens the panel window and activates the recorder on
click. `Ctrl+Shift+E` does the same thing globally, wired up inside
`installDevToolsActivation` in Step 4 — no separate step needed.

## Step 6 — smoke-test it

1. `npm run dev` (or your equivalent), open the app.
2. Click the `esp ⚡` badge, or press `Ctrl+Shift+E`.
3. Confirm a second window/tab opens titled "…DevTools" and shows "connected
   to app" once the recorder installs.
4. Trigger any action that publishes an event in the main app tab (a button
   click, a route change, anything wired through your Router) — confirm it
   appears in the panel's event list within a second or two.
5. Click an event row and confirm the payload (and, once the model's next
   emission lands, the post-dispatch state) render in the detail pane.
6. In production mode (`npm run build` + serve the `dist/` output), load
   the app **without** `?devtools=1` and check the network tab: the
   recorder chunk should **not** be fetched. Then visit
   `?devtools=1` and confirm it now loads and the badge shows active.

## Activation policy recap

No changes needed here — this logic is generic — but worth understanding
before you deploy:

| Mode | Behavior |
|---|---|
| Dev (`NODE_ENV === 'development'`) | Recorder installs automatically, always |
| Prod, no opt-in | Recorder code is never downloaded; badge shows `(off)` |
| Prod, `?devtools=1` | Installs, persists the opt-in to `localStorage`, badge shows active on future visits too |
| Prod, `?devtools=0` | Clears any persisted opt-in |
| Prod, `Ctrl+Shift+E` or badge click | Persists opt-in, opens the panel window, installs the recorder |

## Customization points

All via the second argument to `installDevToolsActivation` /
`installEspDevTools` (`EspDevToolsOptions` in `espDevTools.ts`):

| Option | Default | Use it to |
|---|---|---|
| `backfillSize` | `200` | Increase if you need a panel opened late to catch more history; each entry is a small JSON snapshot so this is cheap to raise |
| `ignoredEvents` | `[]` | Silence very chatty/high-frequency event types (e.g. a clock tick, a mouse-move-driven event) that would otherwise flood the log |
| `reduxDevTools` | `true` | Set `false` to skip the Redux DevTools browser-extension bridge entirely (also good for tests — see the test files' `{ reduxDevTools: false }` pattern, which avoids depending on `window.__REDUX_DEVTOOLS_EXTENSION__`) |
| `exposeOnWindow` | `true` | Set `false` to skip attaching the recorder instance to `window.__ESP_DEVTOOLS__` for console poking |

## Adapting to non-polimer state

`espDevTools.ts`'s `unwrapModel()` calls `model.getImmutableModel()` if
present (the `esp-js-polimer` convention) and falls back to using the model
object as-is otherwise:

```ts
function unwrapModel(model: any): unknown {
  if (model && typeof model.getImmutableModel === 'function') {
    try { return model.getImmutableModel(); } catch { return model; }
  }
  return model;
}
```

If the target project doesn't use `esp-js-polimer` at all, this already
does the right thing — it'll snapshot whatever `Router.getModelObservable`
emits. If your models use some *other* immutability/wrapper convention,
this is the one function to adjust; nothing else in the recorder assumes
polimer.

## Testing

Both test files port over unchanged in spirit — copy their structure, not
necessarily their literal assertions, since they reference Paper-Desk-specific
model/event constants (`registerClockModel`, `EventConst`, `ModelIds`):

- **`espDevTools.test.ts`** demonstrates the pattern of opening a *second*
  `BroadcastChannel` on the same channel name to simulate the standalone
  panel receiving messages, without needing a real second browser context.
  Swap in any minimal event/model from your own project — the assertions
  only need something that calls `router.publishEvent`/`broadcastEvent` and
  `router.addModel`.
- **`activation.test.ts`** covers the `localStorage` + query-param opt-in
  policy in isolation (no Router needed) — this one copies over verbatim
  except for the `STORAGE_KEY` string from Step 2.
- For an end-to-end check (optional), Paper Desk's
  `e2e/tests/devtools.spec.ts` is a Playwright test that clicks the badge,
  waits for the new browser window/page, and asserts a real event
  (`instrumentChosen` in that app's case) appears in it — replace the
  triggered action and expected event name with something from your own
  app.

## Checklist

- [ ] Copied `src/devtools/` (9 files) into the target project
- [ ] Renamed `DEVTOOLS_CHANNEL`, `STORAGE_KEY`, `PANEL_WINDOW_NAME` to the new project's namespace
- [ ] Added the second `devtools` entry + HTML plugin (or Vite `rollupOptions.input`) to the bundler config
- [ ] Confirmed `process.env.NODE_ENV` (or `import.meta.env.DEV` on Vite) resolves correctly at build time
- [ ] Called `installDevToolsActivation(router, {...})` at the composition root, before models register
- [ ] Mounted `<DevToolsLauncher />` inside the Router-providing context
- [ ] Verified dev mode auto-activates and prod mode doesn't download the recorder until opted in
- [ ] Ported (or wrote new) unit tests for the recorder + activation policy
