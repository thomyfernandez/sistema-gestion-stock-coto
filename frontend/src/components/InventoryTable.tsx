import { ArrowRight, Search, SlidersHorizontal } from 'lucide-react';
import { number, stockStatus } from '../format';
import { useStore } from '../store';
import type { Producto } from '../types';
import { SIN_CATEGORIA, TODAS_LAS_CATEGORIAS, TODOS, useFiltroProductos } from '../useFiltroProductos';
import { Badge, Button, Empty, ProductName } from './ui';

const contador = (valor: number | null) => (valor === null ? '—' : number(valor));

export function InventoryTable({ onSelect }: { onSelect: (product: Producto) => void }) {
  const { data } = useStore();
  const filtro = useFiltroProductos();
  if (!data) return null;
  const filtered = data.productos.filter(p => filtro.coincide(p)
    && (filtro.tipo === TODOS || p.tipo === filtro.tipo)
    && (filtro.estado === TODOS || stockStatus(p, data.politicas[p.id]).tone === filtro.estado));
  return <section className="panel inventory-panel">
    <div className="panel-heading">
      <div><h2>Existencias</h2><p>Todos tus artículos, en un solo lugar.</p></div>
      <span className="count-tag">{data.productos.length} artículos</span>
    </div>
    <div className="table-toolbar">
      <div className="search-field">
        <Search size={24} aria-hidden="true" />
        <input aria-label="Buscar artículos" placeholder="Buscar por nombre o código…" value={filtro.busqueda} onChange={e => filtro.setBusqueda(e.target.value)} />
        {filtro.busqueda && <button className="clear-search" onClick={() => filtro.setBusqueda('')} aria-label="Limpiar búsqueda">×</button>}
      </div>
      <div className="filter-group">
        <SlidersHorizontal size={24} aria-hidden="true" />
        <select aria-label="Filtrar por categoría" value={filtro.categoria} onChange={e => filtro.setCategoria(e.target.value)}>
          <option value={TODAS_LAS_CATEGORIAS}>Todas las categorías</option>
          <option value={SIN_CATEGORIA}>Sin categoría</option>
          {data.categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <select aria-label="Filtrar por tipo" value={filtro.tipo} onChange={e => filtro.setTipo(e.target.value)}>
          <option value={TODOS}>Todos los tipos</option>
          <option value="SIMPLE">Artículos simples</option>
          <option value="COMBO">Combos</option>
        </select>
        <select aria-label="Filtrar por estado" value={filtro.estado} onChange={e => filtro.setEstado(e.target.value)}>
          <option value={TODOS}>Todos los estados</option>
          <option value="warning">Stock bajo</option>
          <option value="danger">Sin disponible</option>
          <option value="success">Disponible</option>
        </select>
      </div>
    </div>
    <div className="table-scroll" tabIndex={0} role="region" aria-label="Existencias de productos">
      <table>
        <thead>
          <tr>
            <th scope="col">Producto</th><th scope="col">Categoría</th><th scope="col" className="numeric">Físico</th><th scope="col" className="numeric">Góndola</th>
            <th scope="col" className="numeric">Reservado</th><th scope="col" className="numeric">Disponible</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          {filtered.map(p => {
            const state = stockStatus(p, data.politicas[p.id]);
            return <tr key={p.id}>
              <td><ProductName product={p} /></td>
              <td>{data.categorias.find(c => c.id === p.categoriaId)?.nombre || <span className="caption">Sin categoría</span>}</td>
              <td className="numeric">{contador(p.stockFisico)}</td>
              <td className="numeric">{contador(p.stockExhibicion)}</td>
              <td className="numeric">{contador(p.stockReservado)}</td>
              <td className={`numeric available ${state.tone}`}>{number(p.disponible)}</td>
              <td><Badge tone={state.tone}>{state.label}</Badge></td>
              <td><Button variant="ghost" className="icon-button" icon={ArrowRight} aria-label={`Ver detalle de ${p.nombre}`} onClick={() => onSelect(p)} /></td>
            </tr>;
          })}
        </tbody>
      </table>
    </div>
    {filtered.length === 0 && <Empty action={<Button onClick={filtro.limpiar}>Limpiar filtros</Button>} />}
    <footer className="table-footer"><span>Mostrando {filtered.length} de {data.productos.length} artículos</span><span>Existencias actualizadas desde el inventario</span></footer>
  </section>;
}
