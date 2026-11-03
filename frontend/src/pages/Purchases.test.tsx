import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PurchasesPage } from './Purchases';
import { crearBackend, iniciarSesion } from '../test/backend';
import { renderConStore } from '../test/render';

beforeEach(() => iniciarSesion());
afterEach(() => vi.unstubAllGlobals());

const filas = () => within(screen.getByRole('region', { name: 'Movimientos del inventario' })).getAllByRole('row').slice(1);
const pedidosDeMovimientos = (backend: ReturnType<typeof crearBackend>) => backend.solicitudes.filter(s => s.path === '/movimientos').map(s => s.query.toString());

describe('actividad del inventario', () => {
  it('pagina en el servidor y no descarga el historial completo', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="actividad" />);
    await waitFor(() => expect(filas()).toHaveLength(25));
    expect(pedidosDeMovimientos(backend)).toEqual(['limite=25&pagina=0']);
    expect(screen.getByText('30 registros · Página 1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(filas()).toHaveLength(5));
    expect(screen.getByText('30 registros · Página 2 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    expect(pedidosDeMovimientos(backend).at(-1)).toBe('limite=25&pagina=1');
  });

  it('filtrar por artículo vuelve a la primera página y filtra en el servidor', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="actividad" />);
    await waitFor(() => expect(filas()).toHaveLength(25));
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    await userEvent.selectOptions(screen.getByLabelText('Artículo de actividad'), 'Arroz COTO 1 kg');
    await waitFor(() => expect(screen.getByText('15 registros · Página 1 de 1')).toBeInTheDocument());
    expect(pedidosDeMovimientos(backend).at(-1)).toBe('stockId=1&limite=25&pagina=0');
  });

  it('la auditoría se pide sólo al abrirla', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="actividad" />);
    expect(backend.rutas()).not.toContain('/auditoria');
    await userEvent.click(screen.getByRole('button', { name: 'Auditoría' }));
    await waitFor(() => expect(backend.rutas()).toContain('/auditoria'));
    expect(await screen.findByText('Sin actividad')).toBeInTheDocument();
  });
});
