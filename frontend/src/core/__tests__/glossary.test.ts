import { describe, expect, it } from 'vitest';
import { GLOSSARY } from '../glossary';

describe('glossary', () => {
  it('every term has a non-empty title and description', () => {
    for (const [key, entry] of Object.entries(GLOSSARY)) {
      expect(entry.title.length, `${key} title`).toBeGreaterThan(0);
      expect(entry.description.length, `${key} description`).toBeGreaterThan(20);
    }
  });

  it('covers the core Greeks used across the desk', () => {
    expect(Object.keys(GLOSSARY)).toEqual(expect.arrayContaining(['delta', 'gamma', 'theta', 'vega']));
  });
});
