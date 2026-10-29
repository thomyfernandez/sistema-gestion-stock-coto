import type { Producto, Snapshot } from './types';

export function destinoAviso(data: Snapshot, product: Producto) {
  const politica = data.politicas[product.id];
  const categoria = data.categorias.find(c => c.id === product.categoriaId);
  const particular = politica?.contactoId != null;
  const contactoId = particular ? politica.contactoId : categoria?.contactoId;
  const contacto = data.contactos.find(c => c.id === contactoId);
  const canal = contacto ? (particular ? politica.canalAlerta : categoria?.canalAlerta) || data.avisos.canal : data.avisos.canal;
  return { nombre: contacto?.nombre || 'Sucursal', canal, destino: canal === 'EMAIL' ? contacto?.email || data.avisos.email : contacto?.telefono || data.avisos.telefono, origen: particular ? 'Artículo' : contacto ? `Categoría: ${categoria?.nombre}` : 'Sucursal' };
}
