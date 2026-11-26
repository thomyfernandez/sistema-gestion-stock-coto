import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PolicyForm } from './CatalogForms';
import { crearBackend, iniciarSesion } from '../test/backend';
import { renderConStore } from '../test/render';

beforeEach(() => iniciarSesion());
afterEach(() => vi.unstubAllGlobals());

describe('modal de política de reposición', () => {
  it('sólo pide parámetros de reposición y muestra a quién se avisará', async () => {
    const backend = crearBackend();
    await renderConStore(<PolicyForm product={backend.datos.productos[2]} onClose={() => {}} />);
    expect(screen.getByRole('dialog', { name: 'Política de reposición' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Destinatario del aviso')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Canal del aviso')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Umbral de alerta')).toHaveValue(5);
    expect(screen.getByText('Avisar a Lista de Verdulería · EMAIL: verduleria@coto.example, jefe.verduleria@coto.example')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administrar destinatarios de avisos' })).toHaveAttribute('href', '#/configuracion/avisos');
  });

  it('guarda conservando la asignación anterior y recarga sólo las políticas', async () => {
    const backend = crearBackend();
    const cerrar = vi.fn();
    await renderConStore(<PolicyForm product={backend.datos.productos[1]} onClose={cerrar} />);
    expect(screen.getByText('Avisar a Supervisión de frescos · SMS: +541155552222')).toBeInTheDocument();
    const umbral = screen.getByLabelText('Umbral de alerta');
    await userEvent.clear(umbral);
    await userEvent.type(umbral, '12');
    backend.limpiar();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar política' }));
    await waitFor(() => expect(cerrar).toHaveBeenCalledOnce());
    expect(backend.solicitudes[0]).toMatchObject({ method: 'PUT', path: '/productos/3/politica-reposicion' });
    expect(backend.solicitudes[0].body).toMatchObject({ tipo: 'DEMANDA', umbralAlerta: 12, contactoId: 1000, canalAlerta: 'SMS' });
    expect(backend.rutas()).toEqual(['/politicas']);
  });

  it('estacional: no pide factor y muestra el que rige hoy según el backend', async () => {
    const backend = crearBackend();
    backend.datos.politicas = [...backend.datos.politicas.filter(p => p.stockId !== 1),
      { ...backend.datos.politicas[0], stockId: 1, tipo: 'ESTACIONAL', puntoPedido: 0, stockObjetivo: 100 }];
    backend.datos.factores[1] = { fecha: '2026-12-20', factor: 1.1, origen: 'ARTICULO', temporadaId: 1, temporada: 'Navidad y Reyes' };
    await renderConStore(<PolicyForm product={backend.datos.productos[0]} onClose={() => {}} />);
    expect(screen.queryByLabelText('Factor de temporada')).not.toBeInTheDocument();
    const tarjeta = await screen.findByRole('region', { name: 'Factor de temporada vigente' });
    await waitFor(() => expect(tarjeta).toHaveTextContent('Hoy rige «Navidad y Reyes» · ×1,1'));
    expect(tarjeta).toHaveTextContent('Temporada del artículo.');
    // 100 × 1,1 = 110 exacto: el redondeo hacia arriba no puede dar 111.
    expect(tarjeta).toHaveTextContent('Objetivo de hoy110 unidades');
    expect(tarjeta).toHaveTextContent('al 20/12/2026');
    expect(within(tarjeta).getByRole('link', { name: 'Administrar temporadas' })).toHaveAttribute('href', '#/compras/temporadas');
    await userEvent.clear(screen.getByLabelText('Objetivo base'));
    await userEvent.type(screen.getByLabelText('Objetivo base'), '40');
    expect(tarjeta).toHaveTextContent('Objetivo de hoy44 unidades');
    expect(tarjeta).toHaveTextContent('Guardá la política antes de salir');
  });

  it('estacional: si la consulta del factor falla, permite reintentar y guardar igual', async () => {
    const backend = crearBackend();
    let fallar = true;
    backend.usar(s => s.path.endsWith('/factor-estacional') && fallar ? { status: 500, body: { detail: 'Error' } } : undefined);
    const cerrar = vi.fn();
    await renderConStore(<PolicyForm product={backend.datos.productos[0]} onClose={cerrar} />);
    await userEvent.selectOptions(screen.getByLabelText('Política'), 'ESTACIONAL');
    const tarjeta = await screen.findByRole('region', { name: 'Factor de temporada vigente' });
    await waitFor(() => expect(tarjeta).toHaveTextContent('No pudimos consultar la temporada de hoy.'));
    fallar = false;
    await userEvent.click(within(tarjeta).getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(tarjeta).toHaveTextContent('Hoy no rige ninguna temporada · ×1'));
    expect(tarjeta).toHaveTextContent('Se repone el objetivo base.');
    backend.limpiar();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar política' }));
    await waitFor(() => expect(cerrar).toHaveBeenCalledOnce());
    expect(backend.solicitudes[0].body).toMatchObject({ tipo: 'ESTACIONAL', stockObjetivo: 100 });
    expect(backend.solicitudes[0].body).not.toHaveProperty('factorEstacional');
  });

  it('muestra el error del backend sin cerrar el modal', async () => {
    const backend = crearBackend();
    const cerrar = vi.fn();
    await renderConStore(<PolicyForm product={backend.datos.productos[0]} onClose={cerrar} />);
    backend.usar(s => s.method === 'PUT' ? { status: 400, body: { detail: 'El stock objetivo debe superar el punto de pedido' } } : undefined);
    await userEvent.click(screen.getByRole('button', { name: 'Guardar política' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El stock objetivo debe superar el punto de pedido');
    expect(cerrar).not.toHaveBeenCalled();
  });
});
