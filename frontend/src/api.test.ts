import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, setAuthToken, setOnUnauthorized } from './api';
import { crearBackend } from './test/backend';

afterEach(() => { setAuthToken(null); setOnUnauthorized(() => {}); vi.unstubAllGlobals(); });

describe('cliente HTTP', () => {
  it('envía el token de sesión en cada solicitud', async () => {
    const backend = crearBackend();
    setAuthToken('abc');
    await api.productos();
    expect(backend.solicitudes[0].authorization).toBe('Bearer abc');
  });

  it('convierte los errores RFC 9457 en ApiError con código y detalle', async () => {
    const backend = crearBackend();
    backend.usar(() => ({ status: 400, body: { detail: 'Indicá un email válido', codigo: 'SOLICITUD_INVALIDA', errores: { emails: ['inválido'] } } }));
    const error = await api.guardarDestinatarios(1, ['x']).catch(e => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ message: 'Indicá un email válido', status: 400, codigo: 'SOLICITUD_INVALIDA', errores: { emails: ['inválido'] } });
  });

  it('distingue una consulta sin conexión de una operación con resultado desconocido', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api.productos()).rejects.toMatchObject({ codigo: 'SIN_CONEXION', status: 0 });
    await expect(api.evaluar()).rejects.toMatchObject({ codigo: 'RESULTADO_DESCONOCIDO' });
  });

  it('avisa la sesión vencida salvo en el inicio de sesión', async () => {
    const backend = crearBackend();
    backend.usar(() => ({ status: 401, body: { detail: 'No autenticado' } }));
    const vencida = vi.fn();
    setOnUnauthorized(vencida);
    await expect(api.login({ username: 'x', password: 'y' })).rejects.toBeInstanceOf(ApiError);
    expect(vencida).not.toHaveBeenCalled();
    await expect(api.productos()).rejects.toBeInstanceOf(ApiError);
    expect(vencida).toHaveBeenCalledOnce();
  });

  it('pide movimientos paginados y lee el total de X-Total-Count', async () => {
    const backend = crearBackend();
    const pagina = await api.movimientos({ tipos: ['RECEPCION', 'RESERVA_DIGITAL'], limite: 10 });
    expect(backend.solicitudes[0].query.toString()).toBe('tipo=RECEPCION%2CRESERVA_DIGITAL&limite=10');
    expect(pagina.elementos).toHaveLength(10);
    expect(pagina.total).toBe(30);
    backend.usar(s => s.path === '/auditoria' ? { body: [{ stockId: 1 }] } : undefined);
    expect((await api.auditoria({ stockId: 1, limite: 5, pagina: 2 })).total).toBe(1);
    expect(backend.solicitudes[1].query.toString()).toBe('stockId=1&limite=5&pagina=2');
  });

  it('guarda la lista general o la de una categoría en su ruta', async () => {
    const backend = crearBackend();
    await api.guardarDestinatarios(null, ['a@coto.example']);
    await api.guardarDestinatarios(3, []);
    expect(backend.solicitudes.map(s => `${s.method} ${s.path}`)).toEqual(['PUT /configuracion/destinatarios/general', 'PUT /configuracion/destinatarios/categorias/3']);
    expect(backend.solicitudes[0].body).toEqual({ emails: ['a@coto.example'] });
  });
});
