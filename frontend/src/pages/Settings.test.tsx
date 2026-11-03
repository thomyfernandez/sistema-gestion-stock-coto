import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SettingsPage } from './Settings';
import { crearBackend, iniciarSesion } from '../test/backend';
import { renderConStore } from '../test/render';

beforeEach(() => iniciarSesion());
afterEach(() => vi.unstubAllGlobals());

const fila = (nombre: string) => within(screen.getByRole('region', { name: 'Destinatarios de avisos' })).getByText(nombre).closest('tr')!;

describe('destinatarios de avisos', () => {
  it('lista la general y una por categoría con su estado', async () => {
    crearBackend();
    await renderConStore(<SettingsPage tab="avisos" />);
    expect(within(fila('Lista general')).getByText('Usa el respaldo')).toBeInTheDocument();
    expect(within(fila('Verdulería')).getByText('2 direcciones')).toBeInTheDocument();
    expect(within(fila('Verdulería')).getByText('verduleria@coto.example, jefe.verduleria@coto.example')).toBeInTheDocument();
    expect(within(fila('Almacén')).getByText('Sin emails')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Respaldo de la sucursal' })).toBeInTheDocument();
  });

  it('valida los emails antes de enviar y guarda la lista normalizada', async () => {
    const backend = crearBackend();
    await renderConStore(<SettingsPage tab="avisos" />);
    await userEvent.click(screen.getByRole('button', { name: 'Editar destinatarios de Almacén' }));
    const dialogo = screen.getByRole('dialog', { name: 'Destinatarios · Almacén' });
    const campo = within(dialogo).getByLabelText('Emails');
    await userEvent.type(campo, 'ventas@coto, compras@coto.example');
    backend.limpiar();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar lista' }));
    expect(within(dialogo).getByRole('alert')).toHaveTextContent('Este email no es válido: ventas@coto');
    expect(backend.solicitudes).toHaveLength(0);

    await userEvent.clear(campo);
    await userEvent.type(campo, 'Compras@COTO.example{Enter}compras@coto.example, jefe@coto.example');
    expect(within(dialogo).getByText('2 direcciones')).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar lista' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(backend.solicitudes[0]).toMatchObject({ method: 'PUT', path: '/configuracion/destinatarios/categorias/1', body: { emails: ['compras@coto.example', 'jefe@coto.example'] } });
    expect([...backend.rutas()].sort()).toEqual(['/categorias', '/configuracion/avisos', '/configuracion/contactos', '/configuracion/destinatarios']);
    expect(within(fila('Almacén')).getByText('2 direcciones')).toBeInTheDocument();
  });

  it('vaciar la lista general la deja en respaldo', async () => {
    const backend = crearBackend();
    backend.datos.destinatarios[0] = { categoriaId: null, emails: ['sucursal@coto.example'] };
    await renderConStore(<SettingsPage tab="avisos" />);
    expect(within(fila('Almacén')).getByText('Usa la lista general')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Editar destinatarios de Lista general' }));
    await userEvent.clear(screen.getByLabelText('Emails'));
    expect(screen.getByText('Lista vacía: se usará el siguiente destinatario disponible.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar lista' }));
    await waitFor(() => expect(within(fila('Lista general')).getByText('Usa el respaldo')).toBeInTheDocument());
    expect(backend.solicitudes.find(s => s.method === 'PUT')).toMatchObject({ path: '/configuracion/destinatarios/general', body: { emails: [] } });
  });
});
