import type { Producto, Snapshot } from './types';

export interface DestinatariosResueltos {
  emails: string[];
  origen: 'CATEGORIA' | 'GENERAL' | 'NINGUNO';
  categoriaNombre?: string;
  resumenTexto: string;
}

export function resolverDestinatariosAviso(
  data: Snapshot,
  product: Producto
): DestinatariosResueltos {
  const categoria = data.categorias?.find(c => c.id === product.categoriaId);
  const emailsCategoria = categoria && data.destinatarios?.porCategoria?.[categoria.id];

  if (emailsCategoria && emailsCategoria.length > 0) {
    return {
      emails: emailsCategoria,
      origen: 'CATEGORIA',
      categoriaNombre: categoria.nombre,
      resumenTexto: `${emailsCategoria.length} ${emailsCategoria.length === 1 ? 'email' : 'emails'} (Categoría: ${categoria.nombre})`
    };
  }

  const emailsGeneral = data.destinatarios?.general || [];
  if (emailsGeneral.length > 0) {
    return {
      emails: emailsGeneral,
      origen: 'GENERAL',
      categoriaNombre: categoria?.nombre,
      resumenTexto: `${emailsGeneral.length} ${emailsGeneral.length === 1 ? 'email' : 'emails'} (Lista general)`
    };
  }

  return {
    emails: [],
    origen: 'NINGUNO',
    categoriaNombre: categoria?.nombre,
    resumenTexto: 'Sin destinatarios configurados'
  };
}

export function destinoAviso(data: Snapshot, product: Producto) {
  const resueltos = resolverDestinatariosAviso(data, product);
  if (resueltos.origen === 'CATEGORIA') {
    return {
      nombre: `Categoría ${resueltos.categoriaNombre}`,
      canal: 'EMAIL' as const,
      destino: resueltos.emails.join(', '),
      origen: `Categoría: ${resueltos.categoriaNombre}`
    };
  }
  if (resueltos.origen === 'GENERAL') {
    return {
      nombre: 'Lista general de la sucursal',
      canal: 'EMAIL' as const,
      destino: resueltos.emails.join(', '),
      origen: 'Lista general'
    };
  }
  return {
    nombre: 'Sin destinatarios',
    canal: 'EMAIL' as const,
    destino: '—',
    origen: 'Sin destinatarios'
  };
}
