import { createContext, useContext } from 'react';
import { AppServices } from './bootstrap';

export const AppContext = createContext<AppServices | null>(null);

export function useServices(): AppServices {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('AppContext not provided');
  return ctx;
}
