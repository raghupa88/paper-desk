// esp-js ships a UMD bundle that expects a browser-style global.
(globalThis as any).self = globalThis;
(globalThis as any).window = (globalThis as any).window ?? globalThis;
