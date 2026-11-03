import { act, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';
import { INTERVALO_SONDEO } from './store';
import { crearBackend, iniciarSesion } from './test/backend';
import { renderConStore } from './test/render';

let visibilidad: DocumentVisibilityState = 'visible';
beforeEach(() => {
  iniciarSesion();
  visibilidad = 'visible';
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilidad);
});
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

const ordenadas = (rutas: string[]) => [...rutas].sort();

describe('store', () => {
  it('carga cada recurso una sola vez y todas las políticas en una única solicitud', async () => {
    const backend = crearBackend();
    const { store } = await renderConStore();
    expect(ordenadas(backend.rutas())).toEqual(['/categorias', '/configuracion/avisos', '/configuracion/contactos', '/configuracion/destinatarios', '/pedidos', '/politicas', '/productos']);
    expect(backend.solicitudes.every(s => s.authorization === 'Bearer token-de-prueba')).toBe(true);
    expect(backend.rutas().some(r => r.includes('politica-reposicion') || r === '/movimientos' || r === '/auditoria' || r === '/integraciones')).toBe(false);
    expect(store().data?.politicas[3].tipo).toBe('DEMANDA');
  });

  it('cada 20 s consulta sólo inventario y avanza la revisión únicamente si cambió', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const backend = crearBackend();
    const { store, revision } = await renderConStore();
    const inicial = revision();
    backend.limpiar();
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_SONDEO));
    expect(ordenadas(backend.rutas())).toEqual(['/pedidos', '/productos']);
    expect(revision()).toBe(inicial);
    backend.datos.productos[0] = { ...backend.datos.productos[0], disponible: 79 };
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_SONDEO));
    await waitFor(() => expect(revision()).toBe(inicial + 1));
    expect(store().data?.productos[0].disponible).toBe(79);
  });

  it('no consulta con la pestaña oculta y se pone al día al volver', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const backend = crearBackend();
    await renderConStore();
    backend.limpiar();
    visibilidad = 'hidden';
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_SONDEO * 3));
    expect(backend.solicitudes).toHaveLength(0);
    visibilidad = 'visible';
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    await waitFor(() => expect(ordenadas(backend.rutas())).toEqual(['/pedidos', '/productos']));
  });

  it('después de una operación recarga sólo los grupos indicados y avanza la revisión', async () => {
    const backend = crearBackend();
    const { store, revision } = await renderConStore();
    const inicial = revision();
    backend.limpiar();
    const { stockId: _, ...politica } = backend.datos.politicas[0];
    await act(() => store().run(() => api.guardarPolitica(1, { ...politica, umbralAlerta: 25 }), 'Guardada', ['politicas']));
    expect(backend.rutas('PUT')).toEqual(['/productos/1/politica-reposicion']);
    expect(backend.rutas()).toEqual(['/politicas']);
    expect(store().data?.politicas[1].umbralAlerta).toBe(25);
    expect(store().toast).toEqual({ message: 'Guardada', tone: 'success' });
    expect(revision()).toBe(inicial + 1);
    backend.limpiar();
    await act(() => store().run(async () => 'ok', 'Movimiento confirmado'));
    expect(ordenadas(backend.rutas())).toEqual(['/pedidos', '/productos']);
  });

  it('una operación sin grupos sólo avanza la revisión', async () => {
    const backend = crearBackend();
    const { store, revision } = await renderConStore();
    const inicial = revision();
    backend.limpiar();
    await act(() => store().run(async () => 'ok', 'Promoción guardada', []));
    expect(backend.solicitudes).toHaveLength(0);
    expect(revision()).toBe(inicial + 1);
  });

  it('si falla el inventario informa el error y conserva los datos anteriores', async () => {
    const backend = crearBackend();
    const { store } = await renderConStore();
    backend.usar(s => s.path === '/productos' ? { status: 500, body: { detail: 'Base de datos no disponible' } } : undefined);
    await act(() => expect(store().refresh(['inventario'])).rejects.toThrow('Base de datos no disponible'));
    expect(store().error?.message).toBe('Base de datos no disponible');
    expect(store().data?.productos).toHaveLength(3);
  });

  it('impide dos operaciones simultáneas', async () => {
    crearBackend();
    const { store } = await renderConStore();
    let liberar!: () => void;
    let primera!: Promise<unknown>;
    act(() => { primera = store().run(() => new Promise<void>(r => { liberar = r; }), 'Primera'); });
    await expect(store().run(async () => 'x', 'Segunda')).rejects.toThrow('Hay una operación en curso');
    await act(async () => { liberar(); await primera; });
  });
});
