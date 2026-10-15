import { useEffect, useState } from 'react';

/** Las respuestas de una selección anterior nunca reemplazan la selección vigente. */
export function useResource<T>(key: string, loader: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState<{ key: string; data?: T; error?: Error; loading: boolean }>({ key, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    setState({ key, loading: true });
    loader(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ key, data, loading: false });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ key, error, loading: false });
    });
    return () => controller.abort();
    // La clave describe todos los valores que afectan la solicitud.
  }, [key]);
  return state.key === key ? state : { key, loading: true, data: undefined, error: undefined };
}
