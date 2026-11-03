import { useEffect, useState } from 'react';

/**
 * Las respuestas de una selección anterior nunca reemplazan la selección vigente.
 * `key` describe la selección; si sólo cambia `version` (por ejemplo, la revisión del store),
 * se conservan los datos visibles mientras se recargan.
 */
export function useResource<T>(key: string, loader: (signal: AbortSignal) => Promise<T>, version = 0) {
  const [state, setState] = useState<{ key: string; data?: T; error?: Error; loading: boolean }>({ key, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    setState(old => old.key === key ? { ...old, loading: true } : { key, loading: true });
    loader(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ key, data, loading: false });
    }).catch(error => {
      if (!controller.signal.aborted) setState(old => ({ key, data: old.key === key ? old.data : undefined, error, loading: false }));
    });
    return () => controller.abort();
    // La clave y la versión describen todos los valores que afectan la solicitud.
  }, [key, version]);
  return state.key === key ? state : { key, loading: true, data: undefined, error: undefined };
}
