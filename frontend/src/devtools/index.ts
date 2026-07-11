// Barrel for the code that's safe to statically import from the main app
// bundle. The recorder itself (espDevTools.ts) is intentionally NOT
// re-exported here — it's reached only via the dynamic import() inside
// activation.ts, so a production build that never activates devtools never
// downloads it. Import from './espDevTools' directly (tests, the standalone
// panel) if you need the class itself.
export { installDevToolsActivation, activateAndOpenDevTools, isDevToolsEnabled, openDevToolsWindow } from './activation';
export type { EspDevToolsOptions, EspTraceEvent } from './espDevTools';
