import { screen, waitFor } from '@testing-library/react';
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
