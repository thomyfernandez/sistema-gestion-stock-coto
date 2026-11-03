import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, setAuthToken, setOnUnauthorized } from './api';
import type { Snapshot, UsuarioSesion, RolUsuario } from './types';

/** Datos que se recargan juntos. Cada operación pide sólo los grupos que modifica. */
export type Grupo = 'inventario' | 'politicas' | 'configuracion';
export const GRUPOS: readonly Grupo[] = ['inventario', 'politicas', 'configuracion'];
/** El sondeo periódico sólo trae inventario y se pausa con la pestaña oculta. */
export const INTERVALO_SONDEO = 20000;

const avisosVacios = { canal: 'EMAIL' as const, email: '', telefono: '' };
const cargadores: Record<Grupo, (signal: AbortSignal) => Promise<Partial<Snapshot>>> = {
  inventario: async signal => {
    const [productos, pedidos] = await Promise.all([api.productos(signal), api.pedidos(signal)]);
    return { productos, pedidos };
  },
  politicas: async signal => ({ politicas: Object.fromEntries((await api.politicas(signal)).map(p => [p.stockId, p])) }),
  configuracion: async signal => {
    const [categorias, contactos, avisos, destinatarios] = await Promise.all([
      api.categorias(signal).catch(() => []),
      api.contactos(signal).catch(() => []),
      api.avisos(signal).catch(() => avisosVacios),
      api.destinatarios(signal).catch(() => []),
    ]);
    return { categorias, contactos, avisos, destinatarios };
  },
};
const claves: (keyof Snapshot)[] = ['productos', 'pedidos', 'politicas', 'categorias', 'contactos', 'avisos', 'destinatarios'];
const completo = (parcial: Partial<Snapshot>): parcial is Snapshot => claves.every(k => parcial[k] !== undefined);

type Toast = { message: string; tone: 'success' | 'warning' | 'danger' };
type Mensaje<T> = string | ((value: T) => string);
interface Store {
  data: Snapshot | null; loading: boolean; error: Error | null; busy: boolean; revision: number; updated: Date | null;
  toast: Toast | null; dismissToast: () => void; refresh: (grupos?: readonly Grupo[]) => Promise<void>;
  /** Ejecuta una operación y recarga sólo `grupos` (por defecto, inventario). `revision` avanza siempre. */
  run: <T>(action: () => Promise<T>, message: Mensaje<T>, grupos?: readonly Grupo[]) => Promise<T>;
  act: <T>(action: () => Promise<T>, message: Mensaje<T>, grupos?: readonly Grupo[]) => Promise<T | undefined>;
  usuario: UsuarioSesion | null;
  login: (credenciales: { username: string; password: string }) => Promise<void>;
  logout: () => void;
  tienePermiso: (rolRequerido: RolUsuario | RolUsuario[]) => boolean;
}
const Context = createContext<Store | null>(null);

const STORAGE_KEY = 'coto_stock_auth';

export function StoreProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(() => {
    try {
      const guardado = sessionStorage.getItem(STORAGE_KEY);
      if (guardado) {
        const parsed = JSON.parse(guardado) as UsuarioSesion;
        setAuthToken(parsed.token);
        return parsed;
      }
    } catch {
      // Ignorar error de parsing
    }
    return null;
  });

  const [data, setData] = useState<Snapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [updated, setUpdated] = useState<Date | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const controllers = useRef<Partial<Record<Grupo, AbortController>>>({});
  const cache = useRef<Partial<Snapshot>>({});
  const huella = useRef('');
  const ultimoInventario = useRef(0);
  const pendientes = useRef(0);
  const locked = useRef(false);

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setUsuario(null);
    setData(null);
    cache.current = {}; huella.current = '';
    window.location.hash = '#/';
  }, []);

  useEffect(() => {
    setOnUnauthorized(() => {
      logout();
      setToast({ message: 'La sesión expiró o es inválida. Por favor, iniciá sesión nuevamente.', tone: 'warning' });
    });
  }, [logout]);

  const login = async (credenciales: { username: string; password: string }) => {
    const sesion = await api.login(credenciales);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(sesion));
    setAuthToken(sesion.token);
    setUsuario(sesion);
    setToast({ message: `¡Bienvenido, ${sesion.nombreCompleto}!`, tone: 'success' });
  };

  const tienePermiso = useCallback((rolRequerido: RolUsuario | RolUsuario[]): boolean => {
    if (!usuario) return false;
    if (usuario.rol === 'SUPERVISOR') return true;
    const requeridos = Array.isArray(rolRequerido) ? rolRequerido : [rolRequerido];
    return requeridos.includes(usuario.rol);
  }, [usuario]);

  /** Una carga más reciente del mismo grupo reemplaza a la anterior; los demás grupos no se descartan. */
  const cargar = useCallback(async (grupos: readonly Grupo[], sondeo: boolean) => {
    if (!usuario) { setLoading(false); return; }
    const solicitudes = grupos.map(g => {
      controllers.current[g]?.abort();
      const controller = new AbortController();
      controllers.current[g] = controller;
      return [g, controller] as const;
    });
    pendientes.current++; setLoading(true);
    try {
      const resultados = await Promise.allSettled(solicitudes.map(([g, c]) => cargadores[g](c.signal)));
      const parcial: Partial<Snapshot> = {};
      let fallo: unknown = null;
      resultados.forEach((r, i) => {
        const [g, c] = solicitudes[i];
        if (controllers.current[g] !== c) return;
        if (r.status === 'fulfilled') Object.assign(parcial, r.value);
        else fallo ??= r.reason;
      });
      let cambio = true;
      if (parcial.productos) {
        const nueva = JSON.stringify([parcial.productos, parcial.pedidos]);
        cambio = nueva !== huella.current;
        huella.current = nueva; ultimoInventario.current = Date.now();
      }
      cache.current = { ...cache.current, ...parcial };
      const snapshot = cache.current;
      // Un sondeo sin cambios no vuelve a renderizar las vistas.
      if (completo(snapshot) && Object.keys(parcial).length && (!sondeo || cambio)) setData(snapshot);
      if (fallo) { setError(fallo as Error); throw fallo; }
      setError(null); setUpdated(new Date());
      if (!sondeo || cambio) setRevision(r => r + 1);
    } finally {
      pendientes.current--;
      if (!pendientes.current) setLoading(false);
    }
  }, [usuario]);

  const refresh = useCallback((grupos: readonly Grupo[] = GRUPOS) => cargar(grupos, false), [cargar]);

  useEffect(() => {
    if (!usuario) { setLoading(false); return; }
    void refresh().catch(() => {});
    const sondear = () => {
      if (!locked.current && document.visibilityState === 'visible') void cargar(['inventario'], true).catch(() => {});
    };
    const timer = setInterval(sondear, INTERVALO_SONDEO);
    const alVolver = () => { if (Date.now() - ultimoInventario.current >= INTERVALO_SONDEO) sondear(); };
    document.addEventListener('visibilitychange', alVolver);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', alVolver);
      Object.values(controllers.current).forEach(c => c?.abort());
      controllers.current = {};
    };
  }, [refresh, cargar, usuario]);

  useEffect(() => {
    if (!toast || toast.tone === 'danger') return;
    const timer = setTimeout(() => setToast(null), 8000);
    return () => clearTimeout(timer);
  }, [toast]);

  async function run<T>(action: () => Promise<T>, message: Mensaje<T>, grupos: readonly Grupo[] = ['inventario']): Promise<T> {
    if (locked.current) throw new Error('Hay una operación en curso. Esperá a que finalice.');
    locked.current = true; setBusy(true);
    try {
      const result = await action();
      try {
        await refresh(grupos);
        setToast({ message: typeof message === 'function' ? message(result) : message, tone: 'success' });
      } catch {
        setToast({ message: 'La operación se confirmó, pero no pudimos actualizar la vista. Actualizá los datos antes de continuar.', tone: 'warning' });
      }
      return result;
    } finally { locked.current = false; setBusy(false); }
  }

  async function act<T>(action: () => Promise<T>, message: Mensaje<T>, grupos?: readonly Grupo[]) {
    try { return await run(action, message, grupos); }
    catch (e) { setToast({ message: (e as Error).message, tone: 'danger' }); return undefined; }
  }

  return (
    <Context.Provider value={{
      data, loading, error, busy, revision, updated, toast,
      dismissToast: () => setToast(null), refresh, run, act,
      usuario, login, logout, tienePermiso
    }}>
      {children}
    </Context.Provider>
  );
}

export function useStore() {
  const store = useContext(Context);
  if (!store) throw new Error('El contexto de inventario no está disponible.');
  return store;
}
