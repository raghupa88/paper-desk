import { beforeEach, describe, expect, it } from 'vitest';
import { isDevToolsEnabled } from '../activation';

function setLocation(search: string) {
  Object.defineProperty(window, 'location', {
    value: { ...window.location, search },
    writable: true,
  });
}

describe('devtools activation policy', () => {
  beforeEach(() => {
    localStorage.clear();
    setLocation('');
  });

  it('is off by default (no prior opt-in, no query param)', () => {
    expect(isDevToolsEnabled()).toBe(false);
  });

  it('?devtools=1 enables and persists the opt-in', () => {
    setLocation('?devtools=1');
    expect(isDevToolsEnabled()).toBe(true);
    setLocation(''); // simulate a later visit with no query param at all
    expect(isDevToolsEnabled()).toBe(true);
  });

  it('?devtools=0 clears a previous opt-in', () => {
    localStorage.setItem('paperdesk.devtoolsEnabled', '1');
    setLocation('?devtools=0');
    expect(isDevToolsEnabled()).toBe(false);
    setLocation('');
    expect(isDevToolsEnabled()).toBe(false);
  });
});
