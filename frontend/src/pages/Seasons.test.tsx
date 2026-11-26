import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PurchasesPage } from './Purchases';
import { crearBackend, iniciarSesion } from '../test/backend';
import { renderConStore } from '../test/render';

beforeEach(() => iniciarSesion('COMPRAS'));
afterEach(() => vi.unstubAllGlobals());

const tabla = () => screen.getByRole('region', { name: 'Temporadas de reposición' });

async function completar(dialogo: HTMLElement, { nombre, articulos, desde, hasta, factor }: { nombre: string; articulos: string[]; desde: [string, string]; hasta: [string, string]; factor: string }) {
  const en = within(dialogo);
  await userEvent.type(en.getByLabelText('Nombre de la temporada'), nombre);
  for (const articulo of articulos) await userEvent.click(en.getByRole('checkbox', { name: new RegExp(articulo) }));
  const [desdeGrupo, hastaGrupo] = [en.getByRole('group', { name: 'Desde' }), en.getByRole('group', { name: 'Hasta' })];
  await userEvent.selectOptions(within(desdeGrupo).getByLabelText('Mes'), desde[1]);
  await userEvent.selectOptions(within(desdeGrupo).getByLabelText('Día'), desde[0]);
  await userEvent.selectOptions(within(hastaGrupo).getByLabelText('Mes'), hasta[1]);
  await userEvent.selectOptions(within(hastaGrupo).getByLabelText('Día'), hasta[0]);
  await userEvent.type(en.getByLabelText('Factor de temporada'), factor);
}

describe('temporadas de reposición', () => {
  it('lista alcance, período, factor y la vigencia que informa el backend', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    const fila = await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad y Reyes/ });
    expect(fila).toHaveTextContent('Artículo: Arroz COTO 1 kg');
    expect(fila).toHaveTextContent('15/11 al 06/01');
    expect(fila).toHaveTextContent('Cruza el año');
    expect(fila).toHaveTextContent('×1,5');
    expect(fila).toHaveTextContent('50 % más');
    expect(fila).toHaveTextContent('Vigente hoy');
    // Arroz tiene política de punto de pedido: la temporada todavía no cambia sus pedidos.
    expect(fila).toHaveTextContent('Sin efecto: ningún artículo con política estacional');
    expect(screen.getByText('1 temporada · 1 vigente hoy')).toBeInTheDocument();
    expect(backend.rutas()).toContain('/temporadas');
  });

  it('crea una temporada que cruza el año y recarga políticas y temporadas', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' });
    await completar(dialogo, { nombre: 'Verano', articulos: ['Bananas por kg'], desde: ['20', '12'], hasta: ['28', '2'], factor: '1.25' });
    expect(within(dialogo).getByText('Del 20 de diciembre al 28 de febrero del año siguiente. Cruza el fin de año.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Repone un 25 % más que el objetivo base.')).toBeInTheDocument();
    expect(dialogo.querySelector('.message.warning')).toHaveTextContent('Bananas por kg tiene política Punto de pedido: esta temporada no cambia sus pedidos');
    backend.limpiar();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear temporada' }));
    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    expect(backend.solicitudes[0]).toMatchObject({ method: 'POST', path: '/temporadas', body: {
      nombre: 'Verano', stockIds: [4], categoriaId: null, desdeDia: 20, desdeMes: 12, hastaDia: 28, hastaMes: 2, factor: 1.25 } });
    await waitFor(() => expect(within(tabla()).getByRole('row', { name: /Verano/ })).toHaveTextContent('Cruza el año'));
    expect(backend.rutas()).toEqual(expect.arrayContaining(['/politicas', '/temporadas']));
    expect(backend.rutas()).not.toContain('/productos');
  });

  it('una temporada puede abarcar varios artículos, con buscador', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' });
    const en = within(dialogo);
    expect(en.getByText('Elegí al menos un artículo.')).toBeInTheDocument();
    await userEvent.type(en.getByLabelText('Buscar artículo'), 'lech');
    expect(en.getAllByRole('checkbox')).toHaveLength(1);
    await userEvent.click(en.getByRole('checkbox', { name: /Leche entera/ }));
    await userEvent.clear(en.getByLabelText('Buscar artículo'));
    await userEvent.click(en.getByRole('checkbox', { name: /Bananas/ }));
    expect(en.getByText('2 artículos elegidos')).toBeInTheDocument();
    // Leche tiene política por demanda y Bananas de punto de pedido: se avisa que la temporada no los cambia todavía.
    expect(dialogo.querySelector('.message.warning')).toHaveTextContent('Leche entera COTO 1 l y Bananas por kg no tienen política Estacional');
    await completar(dialogo, { nombre: 'Verano', articulos: [], desde: ['20', '12'], hasta: ['28', '2'], factor: '1.2' });
    await userEvent.click(en.getByRole('button', { name: 'Crear temporada' }));
    await waitFor(() => expect(dialogo).not.toBeInTheDocument());
    expect(backend.solicitudes.find(s => s.method === 'POST')?.body).toMatchObject({ stockIds: [3, 4], categoriaId: null });
    await waitFor(() => expect(within(tabla()).getByRole('row', { name: /Verano/ })).toHaveTextContent('Artículos: Leche entera COTO 1 l y Bananas por kg'));
  });

  it('sin artículos elegidos no envía la temporada', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' });
    await completar(dialogo, { nombre: 'Vacía', articulos: [], desde: ['1', '3'], hasta: ['2', '3'], factor: '1.1' });
    backend.limpiar();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear temporada' }));
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('Elegí al menos un artículo para la temporada.');
    expect(backend.rutas('POST')).toEqual([]);
  });

  it('el día se ajusta al último del mes elegido', async () => {
    crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }));
    const desde = within(screen.getByRole('dialog', { name: 'Nueva temporada' })).getByRole('group', { name: 'Desde' });
    await userEvent.selectOptions(within(desde).getByLabelText('Mes'), '1');
    await userEvent.selectOptions(within(desde).getByLabelText('Día'), '31');
    await userEvent.selectOptions(within(desde).getByLabelText('Mes'), '4');
    expect(within(desde).getByLabelText('Día')).toHaveValue('30');
    await userEvent.selectOptions(within(desde).getByLabelText('Mes'), '2');
    expect(within(desde).getByLabelText('Día')).toHaveValue('29');
  });

  it('una superposición muestra el detalle del backend sin cerrar el modal', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    backend.usar(s => s.method === 'POST' && s.path === '/temporadas' ? { status: 409, body: {
      codigo: 'TEMPORADA_SUPERPUESTA', detail: 'Se superpone con «Navidad y Reyes» (15/11 al 06/01) del mismo artículo. Cambiá las fechas o editá esa temporada.' } } : undefined);
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' });
    await completar(dialogo, { nombre: 'Fiestas', articulos: ['Arroz COTO 1 kg'], desde: ['1', '12'], hasta: ['31', '12'], factor: '2' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear temporada' }));
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('Se superpone con «Navidad y Reyes» (15/11 al 06/01)');
    expect(dialogo).toBeInTheDocument();
    expect(within(dialogo).getByLabelText('Nombre de la temporada')).toHaveValue('Fiestas');
  });

  it('edita y elimina con confirmación', async () => {
    const backend = crearBackend();
    await renderConStore(<PurchasesPage tab="temporadas" />);
    await within(await screen.findByRole('region', { name: 'Temporadas de reposición' })).findByRole('row', { name: /Navidad/ });
    await userEvent.click(screen.getByRole('button', { name: 'Editar temporada Navidad y Reyes' }));
    const edicion = screen.getByRole('dialog', { name: 'Editar temporada' });
    expect(within(edicion).getByLabelText('Factor de temporada')).toHaveValue(1.5);
    const factor = within(edicion).getByLabelText('Factor de temporada');
    await userEvent.clear(factor);
    await userEvent.type(factor, '1.6');
    await userEvent.click(within(edicion).getByRole('button', { name: 'Guardar cambios' }));
    await waitFor(() => expect(edicion).not.toBeInTheDocument());
    expect(backend.solicitudes.find(s => s.method === 'PUT')).toMatchObject({ path: '/temporadas/1', body: { factor: 1.6, stockIds: [1], desdeDia: 15, hastaMes: 1 } });

    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar temporada Navidad y Reyes' }));
    const confirmacion = screen.getByRole('dialog', { name: 'Eliminar temporada Navidad y Reyes' });
    expect(confirmacion).toHaveTextContent('Arroz COTO 1 kg deja de ajustar su objetivo del 15/11 al 06/01.');
    await userEvent.click(within(confirmacion).getByRole('button', { name: 'Eliminar temporada' }));
    await waitFor(() => expect(confirmacion).not.toBeInTheDocument());
    expect(backend.solicitudes.find(s => s.method === 'DELETE')).toMatchObject({ path: '/temporadas/1' });
    // La fila eliminada ya no existe: el foco vuelve a la acción principal.
    await waitFor(() => expect(screen.getByRole('button', { name: 'Nueva temporada' })).toHaveFocus());
    expect(await screen.findByText('Todavía no hay temporadas')).toBeInTheDocument();
  });
});
