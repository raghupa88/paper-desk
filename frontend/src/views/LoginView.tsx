import React, { useState } from 'react';
import { useServices } from '../AppContext';

export function LoginView() {
  const { dataService } = useServices();
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<'STUDENT' | 'INSTRUCTOR'>('STUDENT');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'login') await dataService.login(email, password);
      else await dataService.signup(email, password, displayName, role);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center">
      <form onSubmit={submit} className="panel p-8 w-96 space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-desk-accent">Paper Desk</h1>
          <p className="text-desk-dim text-sm mt-1">
            Practice trading equities, options, futures, FX &amp; swaps — 100% simulated
            money and market data. Nothing here is real.
          </p>
        </div>
        <div className="flex gap-1">
          <button type="button" className={`btn flex-1 ${mode === 'login' ? 'btn-accent' : ''}`}
                  onClick={() => setMode('login')}>Log in</button>
          <button type="button" className={`btn flex-1 ${mode === 'signup' ? 'btn-accent' : ''}`}
                  onClick={() => setMode('signup')}>Sign up</button>
        </div>
        {mode === 'signup' && (
          <>
            <label htmlFor="displayName" className="sr-only">Display name</label>
            <input id="displayName" className="input" placeholder="Display name" value={displayName}
                   onChange={e => setDisplayName(e.target.value)} required />
            <label htmlFor="role" className="sr-only">I am a</label>
            <select id="role" className="input" value={role} onChange={e => setRole(e.target.value as any)}>
              <option value="STUDENT">I'm a student</option>
              <option value="INSTRUCTOR">I'm an instructor</option>
            </select>
          </>
        )}
        <label htmlFor="email" className="sr-only">Email</label>
        <input id="email" className="input" type="email" placeholder="Email" value={email}
               onChange={e => setEmail(e.target.value)} required />
        <label htmlFor="password" className="sr-only">Password</label>
        <input id="password" className="input" type="password" placeholder="Password (min 6 chars)" value={password}
               onChange={e => setPassword(e.target.value)} required minLength={6} />
        {error && <div className="text-desk-down text-sm" role="alert">{error}</div>}
        <button className="btn btn-accent w-full" disabled={busy}>
          {busy ? 'Working…' : mode === 'login' ? 'Log in' : 'Create account'}
        </button>
      </form>
    </div>
  );
}
