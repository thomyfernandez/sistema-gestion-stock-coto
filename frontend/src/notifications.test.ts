import { describe, expect, it } from 'vitest';
import { destinoAviso } from './notifications';
import { datosIniciales } from './test/backend';
import type { Snapshot } from './types';

function snapshot(cambios: Partial<Snapshot> = {}): Snapshot {
  const d = datosIniciales();
  return { productos: d.productos, pedidos: d.pedidos, politicas: Object.fromEntries(d.politicas.map(p => [p.stockId, p])), categorias: d.categorias,
    contactos: d.contactos, avisos: d.avisos, destinatarios: d.destinatarios, ...cambios };
}
const producto = (data: Snapshot, id: number) => data.productos.find(p => p.id === id)!;

describe('destino de los avisos', () => {
  it('prioriza la lista de la categoría', () => {
    const data = snapshot();
    expect(destinoAviso(data, producto(data, 4))).toEqual({ nombre: 'Lista de Verdulería', canal: 'EMAIL', destinos: ['verduleria@coto.example', 'jefe.verduleria@coto.example'], origen: 'Categoría: Verdulería' });
  });
  it('usa la lista general cuando la categoría no tiene emails, aunque exista un contacto anterior', () => {
    const data = snapshot({ destinatarios: [{ categoriaId: null, emails: ['sucursal@coto.example'] }] });
    expect(destinoAviso(data, producto(data, 3))).toMatchObject({ nombre: 'Lista general', canal: 'EMAIL', destinos: ['sucursal@coto.example'] });
  });
  it('sin listas, recurre al contacto anterior del artículo y luego al respaldo de la sucursal', () => {
    const data = snapshot({ destinatarios: [] });
    expect(destinoAviso(data, producto(data, 3))).toEqual({ nombre: 'Supervisión de frescos', canal: 'SMS', destinos: ['+541155552222'], origen: 'Artículo · respaldo' });
    expect(destinoAviso(data, producto(data, 1))).toEqual({ nombre: 'Sucursal', canal: 'EMAIL', destinos: ['compras@coto.example'], origen: 'Respaldo de la sucursal' });
  });
});
