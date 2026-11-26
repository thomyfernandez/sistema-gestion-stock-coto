import { useState } from 'react';
import type { Producto } from './types';

/** Valores centinela de los filtros: no corresponden a ningún id ni tipo real. */
export const TODAS_LAS_CATEGORIAS = 'TODAS';
export const SIN_CATEGORIA = 'SIN_CATEGORIA';
export const TODOS = 'TODOS';

const normalizar = (texto: string) => texto.toLocaleLowerCase('es');

/**
 * Búsqueda, categoría, tipo y estado de las tablas de artículos. `coincide` resuelve búsqueda y categoría;
 * cada tabla interpreta `tipo` y `estado` según sus propias opciones.
 */
export function useFiltroProductos() {
  const [busqueda, setBusqueda] = useState('');
  const [categoria, setCategoria] = useState(TODAS_LAS_CATEGORIAS);
  const [tipo, setTipo] = useState(TODOS);
  const [estado, setEstado] = useState(TODOS);

  const coincideCategoria = (p: Producto) => {
    if (categoria === TODAS_LAS_CATEGORIAS) return true;
    if (categoria === SIN_CATEGORIA) return p.categoriaId === null;
    return p.categoriaId === Number(categoria);
  };
  const coincide = (p: Producto) => normalizar(`${p.nombre} ${p.id} ${p.descripcion}`).includes(normalizar(busqueda)) && coincideCategoria(p);
  const limpiar = () => { setBusqueda(''); setCategoria(TODAS_LAS_CATEGORIAS); setTipo(TODOS); setEstado(TODOS); };

  return { busqueda, setBusqueda, categoria, setCategoria, tipo, setTipo, estado, setEstado, coincide, limpiar };
}
