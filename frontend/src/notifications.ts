import type { Canal, Producto, Snapshot } from './types';

export interface DestinoAviso { nombre: string; canal: Canal; destinos: string[]; origen: string }

/** Misma prioridad que el backend: lista de la categoría, lista general y, si ambas están vacías, la asignación anterior. */
export function destinoAviso(data: Snapshot, product: Producto): DestinoAviso {
  const categoria = data.categorias.find(c => c.id === product.categoriaId);
  const listaCategoria = categoria && data.destinatarios.find(l => l.categoriaId === categoria.id);
  if (categoria && listaCategoria?.emails.length) return { nombre: `Lista de ${categoria.nombre}`, canal: 'EMAIL', destinos: listaCategoria.emails, origen: `Categoría: ${categoria.nombre}` };
  const general = data.destinatarios.find(l => l.categoriaId === null);
  if (general?.emails.length) return { nombre: 'Lista general', canal: 'EMAIL', destinos: general.emails, origen: 'Sucursal' };
  return respaldo(data, product);
}

/** Destino previo a las listas: contacto del artículo, contacto de la categoría o destinatario general. */
export function respaldo(data: Snapshot, product: Producto): DestinoAviso {
  const politica = data.politicas[product.id];
  const categoria = data.categorias.find(c => c.id === product.categoriaId);
  const particular = politica?.contactoId != null;
  const contactoId = particular ? politica.contactoId : categoria?.contactoId;
  const contacto = data.contactos.find(c => c.id === contactoId);
  const canal = contacto ? (particular ? politica.canalAlerta : categoria?.canalAlerta) || data.avisos.canal : data.avisos.canal;
  const destino = canal === 'EMAIL' ? contacto?.email || data.avisos.email : contacto?.telefono || data.avisos.telefono;
  return { nombre: contacto?.nombre || 'Sucursal', canal, destinos: destino ? [destino] : [], origen: particular ? 'Artículo · respaldo' : contacto ? `Categoría: ${categoria?.nombre} · respaldo` : 'Respaldo de la sucursal' };
}
