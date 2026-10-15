import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, ApiError } from './api';
import type { Snapshot } from './types';

type Toast = { message: string; tone: 'success' | 'warning' | 'danger' };
interface Store {
  data: Snapshot | null; loading: boolean; error: Error | null; busy: boolean; revision: number; updated: Date | null;
  toast: Toast | null; dismissToast: () => void; refresh: () => Promise<void>;
  run: <T>(action: () => Promise<T>, message: string | ((value: T) => string)) => Promise<T>;
  act: <T>(action: () => Promise<T>, message: string | ((value: T) => string)) => Promise<T | undefined>;
}
const Context = createContext<Store | null>(null);
export function StoreProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<Snapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [updated, setUpdated] = useState<Date | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const current = useRef<AbortController | null>(null);
  const locked = useRef(false);
  const refresh = useCallback(async () => {
    current.current?.abort();
    const controller = new AbortController();
    current.current = controller;
    setLoading(true);
    try {
      const [productos, pedidos, movimientos, auditoria, integraciones] = await Promise.all([
        api.productos(controller.signal), api.pedidos(controller.signal), api.movimientos(controller.signal),
        api.auditoria(controller.signal), api.integraciones(controller.signal),
      ]);
      const entries = await Promise.all(productos.filter(p => p.tipo === 'SIMPLE').map(async p => {
        try { return [p.id, await api.politica(p.id, controller.signal)] as const; }
        catch (e) { if (e instanceof ApiError && e.status === 404) return null; throw e; }
      }));
      if (controller.signal.aborted) return;
      setData({ productos, pedidos, movimientos, auditoria, integraciones, politicas: Object.fromEntries(entries.filter(e => e !== null)) });
      setError(null); setUpdated(new Date()); setRevision(r => r + 1);
    } catch (e) {
      if (!controller.signal.aborted) { setError(e as Error); throw e; }
    } finally { if (!controller.signal.aborted) setLoading(false); }
  }, []);
  useEffect(() => {
    void refresh().catch(() => {});
    const timer = setInterval(() => { if (!locked.current) void refresh().catch(() => {}); }, 20000);
    return () => { clearInterval(timer); current.current?.abort(); };
  }, [refresh]);
  useEffect(() => {
    if (!toast || toast.tone === 'danger') return;
    const timer = setTimeout(() => setToast(null), 8000);
    return () => clearTimeout(timer);
  }, [toast]);
  async function run<T>(action: () => Promise<T>, message: string | ((value: T) => string)): Promise<T> {
    if (locked.current) throw new Error('Hay una operación en curso. Esperá a que finalice.');
    locked.current = true; setBusy(true);
    try {
      const result = await action();
      try {
        await refresh();
        setToast({ message: typeof message === 'function' ? message(result) : message, tone: 'success' });
      } catch {
        setToast({ message: 'La operación se confirmó, pero no pudimos actualizar la vista. Actualizá los datos antes de continuar.', tone: 'warning' });
      }
      return result;
    } finally { locked.current = false; setBusy(false); }
  }
  async function act<T>(action: () => Promise<T>, message: string | ((value: T) => string)) {
    try { return await run(action, message); }
    catch (e) { setToast({ message: (e as Error).message, tone: 'danger' }); return undefined; }
  }
  return <Context.Provider value={{ data, loading, error, busy, revision, updated, toast, dismissToast: () => setToast(null), refresh, run, act }}>{children}</Context.Provider>;
}
export function useStore() {
  const store = useContext(Context);
  if (!store) throw new Error('El contexto de inventario no está disponible.');
  return store;
}
