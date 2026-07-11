import React from 'react';
import { createRoot } from 'react-dom/client';
import { PanelApp } from './PanelApp';

// Standalone entry point (webpack "devtools" chunk, served as /devtools.html).
// Deliberately has zero imports from the host app — the only coupling to
// Paper Desk is the shared wire protocol in ../protocol.ts.
createRoot(document.getElementById('root')!).render(<PanelApp />);
