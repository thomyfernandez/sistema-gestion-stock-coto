import { vi } from 'vitest';
import type { Categoria, ConfiguracionAvisos, Contacto, ListaDestinatarios, Movimiento, Pedido, Politica, Producto } from '../types';

/** Backend en memoria para las pruebas: responde como la API real y registra cada solicitud. */
export interface Solicitud { method: string; path: string; query: URLSearchParams; body: unknown; authorization: string | null }
type Respuesta = { status?: number; body?: unknown; headers?: Record<string, string> };
type Ruta = (s: Solicitud) => Respuesta | undefined;

const producto = (id: number, nombre: string, categoriaId: number | null, disponible: number, extra: Partial<Producto> = {}): Producto => ({
  id, categoriaId, nombre, descripcion: nombre, precio: 1000, tipo: 'SIMPLE', disponible, stockFisico: disponible, stockExhibicion: 0, stockReservado: 0,
  componentes: [], imagenUrl: null, ...extra,
});
const politica = (stockId: number, extra: Partial<Politica> = {}): Politica => ({
  stockId, tipo: 'PUNTO_PEDIDO', puntoPedido: 20, stockObjetivo: 100, demandaDiaria: 0, diasCobertura: 1, factorEstacional: 1,
  umbralAlerta: 10, destinoAlerta: 'configuracion-global', contactoId: null, canalAlerta: null, ...extra,
});

export function datosIniciales() {
  return {
    productos: [producto(1, 'Arroz COTO 1 kg', 1, 80), producto(3, 'Leche entera COTO 1 l', 2, 6), producto(4, 'Bananas por kg', 3, 16)],
    pedidos: [{ id: 1, stockId: 3, fecha: '2026-10-15T12:01:00Z', cantidad: 30, estado: 'ENVIADO' }] as Pedido[],
    politicas: [politica(1, { umbralAlerta: 20 }), politica(3, { tipo: 'DEMANDA', demandaDiaria: 12, diasCobertura: 3, puntoPedido: 0, contactoId: 1000, canalAlerta: 'SMS' }), politica(4, { umbralAlerta: 5 })],
    categorias: [{ id: 1, nombre: 'Almacén', contactoId: null, canalAlerta: null }, { id: 2, nombre: 'Frescos', contactoId: null, canalAlerta: null },
      { id: 3, nombre: 'Verdulería', contactoId: null, canalAlerta: null }] as Categoria[],
    contactos: [{ id: 1000, nombre: 'Supervisión de frescos', email: '', telefono: '+541155552222' }] as Contacto[],
    avisos: { canal: 'EMAIL', email: 'compras@coto.example', telefono: '+541100000000' } as ConfiguracionAvisos,
    destinatarios: [{ categoriaId: null, emails: [] }, { categoriaId: 3, emails: ['verduleria@coto.example', 'jefe.verduleria@coto.example'] }] as ListaDestinatarios[],
    movimientos: Array.from({ length: 30 }, (_, i) => ({
      id: 30 - i, stockId: i % 2 ? 1 : 3, tipo: i % 3 ? 'RESERVA_DIGITAL' : 'RECEPCION', cantidad: i + 1,
      fecha: new Date(Date.UTC(2026, 9, 15, 12, 30 - i)).toISOString(), motivo: `Movimiento ${30 - i}`, usuario: 'demo',
    })) as Movimiento[],
  };
}

export function crearBackend() {
  const datos = datosIniciales();
  const solicitudes: Solicitud[] = [];
  const rutas: Ruta[] = [];
  const json = (body: unknown, extra: Omit<Respuesta, 'body'> = {}): Respuesta => ({ body, ...extra });
  const base: Ruta = ({ method, path, query, body }) => {
    if (method === 'GET') switch (path) {
      case '/productos': return json(datos.productos);
      case '/pedidos': return json(datos.pedidos);
      case '/politicas': return json(datos.politicas);
      case '/categorias': return json(datos.categorias);
      case '/configuracion/contactos': return json(datos.contactos);
      case '/configuracion/avisos': return json(datos.avisos);
      case '/configuracion/destinatarios': return json(datos.destinatarios);
      case '/integraciones': return json({ entregas: [], proveedoresDisponibles: { ERP: true, MENSAJERIA: true, COTO_DIGITAL: true }, mensajes: [], pedidosERP: [], catalogoDigital: {} });
      case '/movimientos': {
        const tipos = query.get('tipo')?.split(',');
        const stockId = query.get('stockId');
        const filtrados = datos.movimientos.filter(m => (!tipos || tipos.includes(m.tipo)) && (!stockId || m.stockId === Number(stockId)));
        const limite = Number(query.get('limite')), pagina = Number(query.get('pagina') || 0);
        return json(filtrados.slice(pagina * limite, (pagina + 1) * limite), { headers: { 'X-Total-Count': String(filtrados.length) } });
      }
      case '/auditoria': return json([], { headers: { 'X-Total-Count': '0' } });
    }
    const politicaPut = method === 'PUT' && path.match(/^\/productos\/(\d+)\/politica-reposicion$/);
    if (politicaPut) {
      const nueva = { ...(body as Omit<Politica, 'stockId'>), stockId: Number(politicaPut[1]) };
      datos.politicas = [...datos.politicas.filter(p => p.stockId !== nueva.stockId), nueva];
      return json(nueva);
    }
    const lista = method === 'PUT' && path.match(/^\/configuracion\/destinatarios\/(general|categorias\/(\d+))$/);
    if (lista) {
      const nueva = { categoriaId: lista[2] ? Number(lista[2]) : null, emails: (body as { emails: string[] }).emails };
      datos.destinatarios = [...datos.destinatarios.filter(l => l.categoriaId !== nueva.categoriaId), nueva];
      return json(nueva);
    }
    return undefined;
  };
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost');
    const headers = new Headers(init.headers);
    const solicitud: Solicitud = {
      method: init.method || 'GET', path: url.pathname.replace(/^\/api/, ''), query: url.searchParams,
      body: init.body ? JSON.parse(String(init.body)) : undefined, authorization: headers.get('Authorization'),
    };
    solicitudes.push(solicitud);
    const respuesta = [...rutas, base].reduce<Respuesta | undefined>((r, ruta) => r ?? ruta(solicitud), undefined)
      ?? { status: 404, body: { detail: `Sin ruta para ${solicitud.method} ${solicitud.path}` } };
    const status = respuesta.status ?? 200;
    return new Response(status === 204 ? null : JSON.stringify(respuesta.body ?? null), { status, headers: { 'Content-Type': 'application/json', ...respuesta.headers } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    datos, solicitudes, fetchMock,
    /** Intercepta antes que las rutas base, por ejemplo para simular errores. */
    usar(ruta: Ruta) { rutas.unshift(ruta); },
    rutas: (method = 'GET') => solicitudes.filter(s => s.method === method).map(s => s.path),
    limpiar() { solicitudes.length = 0; },
  };
}

export function iniciarSesion(rol: 'SUPERVISOR' | 'COMPRAS' = 'SUPERVISOR') {
  sessionStorage.setItem('coto_stock_auth', JSON.stringify({ token: 'token-de-prueba', username: 'supervisor.demo', nombreCompleto: 'Supervisor de Sucursal', rol }));
}
