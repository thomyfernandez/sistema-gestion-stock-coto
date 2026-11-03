import type { Producto, Pedido, TipoMovimiento, Movimiento, Operacion, ResultadoMovimiento, Auditoria, Politica, Promocion, Precio, ItemCatalogo, Canal, Sistema, Integraciones, Contacto, Categoria, ConfiguracionAvisos, DestinatariosAvisos, ImagenCarga, RolUsuario, UsuarioSesion } from './types';

export class ApiError extends Error {
  constructor(message: string, public status: number, public codigo = '', public errores: Record<string, string[]> = {}) { super(message); }
}
const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export const imagenSrc = (url: string) => `${base}${url}`;

let authToken: string | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function setOnUnauthorized(cb: () => void) {
  onUnauthorizedCallback = cb;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const mutation = options.method && options.method !== 'GET';
  const timeout = AbortSignal.timeout(20000);
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
  let response: Response;
  try {
    const headers: Record<string, string> = {
      ...(options.headers as Record<string, string> || {}),
    };
    if (options.body) {
      headers['Content-Type'] = 'application/json';
    }
    if (authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    response = await fetch(`${base}/api${path}`, {
      ...options,
      signal,
      headers,
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(mutation
      ? 'No pudimos confirmar el resultado. Revisá los movimientos antes de repetir la operación.'
      : 'No pudimos conectarnos al backend. Comprobá que esté iniciado y volvé a intentar.', 0, mutation ? 'RESULTADO_DESCONOCIDO' : 'SIN_CONEXION');
  }
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => null);
  if (response.status === 401 && !path.startsWith('/auth/login')) {
    onUnauthorizedCallback?.();
  }
  if (!response.ok) throw new ApiError(body?.detail || `No se pudo completar la solicitud (${response.status}).`, response.status, body?.codigo, body?.errores);
  if (body === null) throw new ApiError('El servidor devolvió una respuesta inesperada.', response.status);
  return body as T;
}
const json = (method: string, body?: unknown): RequestInit => ({ method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
const query = (values: Record<string, string | number>) => new URLSearchParams(Object.entries(values).map(([k, v]) => [k, String(v)])).toString();

export const api = {
  login: (credenciales: { username: string; password: string }) => request<UsuarioSesion>('/auth/login', json('POST', credenciales)),
  perfil: (signal?: AbortSignal) => request<{ username: string; nombreCompleto: string; rol: RolUsuario }>('/auth/perfil', { signal }),
  categorias: (signal?: AbortSignal) => request<Categoria[]>('/categorias', { signal }),
  guardarCategoria: (body: Omit<Categoria, 'id'>, id?: number) => request<Categoria>(`/categorias${id ? `/${id}` : ''}`, json(id ? 'PUT' : 'POST', body)),
  asignarCategoria: (id: number, categoriaId: number | null) => request<Producto>(`/productos/${id}/categoria`, json('PUT', { categoriaId })),
  contactos: (signal?: AbortSignal) => request<Contacto[]>('/configuracion/contactos', { signal }),
  guardarContacto: (body: Omit<Contacto, 'id'>, id?: number) => request<Contacto>(`/configuracion/contactos${id ? `/${id}` : ''}`, json(id ? 'PUT' : 'POST', body)),
  avisos: (signal?: AbortSignal) => request<ConfiguracionAvisos>('/configuracion/avisos', { signal }),
  guardarAvisos: (body: ConfiguracionAvisos) => request<ConfiguracionAvisos>('/configuracion/avisos', json('PUT', body)),
  destinatarios: (signal?: AbortSignal) => request<DestinatariosAvisos>('/configuracion/destinatarios', { signal }),
  guardarDestinatariosGeneral: (emails: string[]) => request<string[]>('/configuracion/destinatarios/general', json('PUT', { emails })),
  guardarDestinatariosCategoria: (categoriaId: number, emails: string[]) => request<string[]>(`/configuracion/destinatarios/categorias/${categoriaId}`, json('PUT', { emails })),
  productos: (signal?: AbortSignal) => request<Producto[]>('/productos', { signal }),
  pedidos: (signal?: AbortSignal) => request<Pedido[]>('/pedidos', { signal }),
  movimientos: (signal?: AbortSignal) => request<Movimiento[]>('/movimientos', { signal }),
  auditoria: (signal?: AbortSignal) => request<Auditoria[]>('/auditoria', { signal }),
  integraciones: (signal?: AbortSignal) => request<Integraciones>('/integraciones', { signal }),
  politica: (id: number, signal?: AbortSignal) => request<Politica>(`/productos/${id}/politica-reposicion`, { signal }),
  guardarPolitica: (id: number, body: Omit<Politica, 'stockId'>) => request<Politica>(`/productos/${id}/politica-reposicion`, json('PUT', body)),
  promociones: (id: number, signal?: AbortSignal) => request<Promocion[]>(`/productos/${id}/promociones`, { signal }),
  guardarPromocion: (id: number, body: Omit<Promocion, 'id' | 'productoId'>, promocionId?: number) => request<Promocion>(`/productos/${id}/promociones${promocionId ? `/${promocionId}` : ''}`, json(promocionId ? 'PUT' : 'POST', body)),
  precio: (id: number, cantidad: number, medioPago: string, signal?: AbortSignal) => request<Precio>(`/productos/${id}/precio?${query({ cantidad, medioPago })}`, { signal }),
  catalogo: (medioPago: string, signal?: AbortSignal) => request<ItemCatalogo[]>(`/digital/catalogo?${query({ medioPago })}`, { signal }),
  crearProducto: (body: { nombre: string; descripcion: string; precio: number; categoriaId: number | null; imagen?: ImagenCarga }) => request<Producto>('/productos', json('POST', body)),
  crearCombo: (body: { nombre: string; descripcion: string; precio: number; componentes: { stockId: number; cantidad: number }[]; categoriaId: number | null; imagen?: ImagenCarga }) => request<Producto>('/combos', json('POST', body)),
  armarCombo: (id: number, body: { cantidad: number; motivo: string; usuario: string }) => request<ResultadoMovimiento>(`/combos/${id}/armado`, json('POST', body)),
  desarmarCombo: (id: number, body: { cantidad: number; motivo: string; usuario: string }) => request<ResultadoMovimiento>(`/combos/${id}/desarmado`, json('POST', body)),
  ingresar: (body: Operacion) => request<ResultadoMovimiento>('/recepcion/ingresos', json('POST', body)),
  mover: (body: Operacion & { tipo: TipoMovimiento }) => request<ResultadoMovimiento>('/sucursal/movimientos', json('POST', body)),
  digital: (accion: 'reservas' | 'cancelaciones' | 'despachos', body: Operacion) => request<ResultadoMovimiento>(`/digital/${accion}`, json('POST', body)),
  evaluar: () => request<Pedido[]>('/reposicion/evaluaciones', json('POST')),
  recibirPedido: (id: number, usuario: string) => request<ResultadoMovimiento>(`/pedidos/${id}/recepcion`, json('POST', { usuario })),
  cancelarPedido: (id: number) => request<void>(`/pedidos/${id}/cancelacion`, json('POST')),
  canal: (signal?: AbortSignal) => request<{ canal: Canal }>('/notificaciones/canal', { signal }),
  guardarCanal: (canal: Canal) => request<{ canal: Canal }>('/notificaciones/canal', json('PUT', { canal })),
  reintentar: () => request<{ procesados: number }>('/integraciones/reintentos', json('POST')),
  proveedor: (sistema: Sistema, disponible: boolean) => request(`/demo/proveedores/${sistema}`, json('PATCH', { disponible })),
};
