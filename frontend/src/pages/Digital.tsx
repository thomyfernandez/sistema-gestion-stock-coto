import { useState } from 'react';
import { ArrowRight, ArrowRightLeft, Search } from 'lucide-react';
import { date, movementLabels, number, sku } from '../format';
import { api } from '../api';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Evento, Integraciones, Movimiento, Producto } from '../types';
import { SIN_CATEGORIA, TODAS_LAS_CATEGORIAS, TODOS, useFiltroProductos } from '../useFiltroProductos';
import { OperationForm } from '../components/OperationForm';
import { Badge, Button, Empty, Modal } from '../components/ui';

function syncStatus(product: Producto, published: string | undefined, pending: boolean) {
  if (pending) return { key: 'PENDIENTE', label: 'Pendiente', tone: 'warning' as const };
  if (published === undefined) return { key: 'SIN_ENVIO', label: 'Sin envío', tone: 'neutral' as const };
  if (Number(published) !== product.disponible) return { key: 'DESACTUALIZADO', label: 'Desactualizado', tone: 'danger' as const };
  return { key: 'SINCRONIZADO', label: 'Sincronizado', tone: 'success' as const };
}

function AvailabilityTable({ products, integration, pending, onOperate }: {
  products: Producto[]; integration: Integraciones; pending: Evento[]; onOperate?: (id: number) => void;
}) {
  const { data } = useStore();
  const filtro = useFiltroProductos();
  const pendingIds = new Set(pending.map(e => e.stockId));
  const coincideTipo = (p: Producto) => filtro.tipo === TODOS || p.tipo === filtro.tipo
    || (filtro.tipo === 'RESERVADOS' && p.tipo === 'SIMPLE' && (p.stockReservado || 0) > 0);
  const rows = products.filter(p => filtro.coincide(p) && coincideTipo(p)
    && (filtro.estado === TODOS || syncStatus(p, integration.catalogoDigital[p.id], pendingIds.has(p.id)).key === filtro.estado));
  return <section className="panel digital-stock">
    <div className="panel-heading">
      <div><h2>Stock del canal digital</h2><p>Compará las existencias locales con el último valor publicado.</p></div>
      <span className="count-tag">{products.length} artículos</span>
    </div>
    <div className="table-toolbar">
      <div className="search-field">
        <Search size={24} aria-hidden="true" />
        <input aria-label="Buscar stock digital" placeholder="Buscar por nombre o código…" value={filtro.busqueda} onChange={e => filtro.setBusqueda(e.target.value)} />
      </div>
      <div className="filter-group">
        <select aria-label="Filtrar stock digital por categoría" value={filtro.categoria} onChange={e => filtro.setCategoria(e.target.value)}>
          <option value={TODAS_LAS_CATEGORIAS}>Todas las categorías</option>
          <option value={SIN_CATEGORIA}>Sin categoría</option>
          {data?.categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <select aria-label="Filtrar stock digital" value={filtro.tipo} onChange={e => filtro.setTipo(e.target.value)}>
          <option value={TODOS}>Todos los artículos</option>
          <option value="RESERVADOS">Simples con reservas</option>
          <option value="SIMPLE">Artículos simples</option>
          <option value="COMBO">Combos</option>
        </select>
        <select aria-label="Estado de sincronización" value={filtro.estado} onChange={e => filtro.setEstado(e.target.value)}>
          <option value={TODOS}>Todos los estados</option>
          <option value="PENDIENTE">Pendiente</option>
          <option value="SINCRONIZADO">Sincronizado</option>
          <option value="DESACTUALIZADO">Desactualizado</option>
          <option value="SIN_ENVIO">Sin envío</option>
        </select>
      </div>
    </div>
    <div className="table-scroll" role="region" aria-label="Stock y reservas digitales" tabIndex={0}>
      <table>
        <thead>
          <tr>
            <th scope="col">Artículo</th><th scope="col" className="numeric">Reservado</th><th scope="col" className="numeric">Disponible local</th>
            <th scope="col" className="numeric">Publicado online</th><th scope="col">Sincronización</th>
            {onOperate && <th scope="col"><span className="sr-only">Acciones</span></th>}
          </tr>
        </thead>
        <tbody>
          {rows.map(p => {
            const published = integration.catalogoDigital[p.id];
            const state = syncStatus(p, published, pendingIds.has(p.id));
            return <tr key={p.id}>
              <td><div className="stacked-cell"><strong>{p.nombre}</strong><span className="caption">{sku(p.id)} · {p.tipo === 'COMBO' ? 'Combo · reservas en sus componentes' : 'Artículo simple'}</span></div></td>
              <td className="numeric">{p.stockReservado === null ? '—' : number(p.stockReservado)}</td>
              <td className="numeric available">{number(p.disponible)}</td>
              <td className="numeric">{published === undefined ? '—' : number(Number(published))}</td>
              <td><Badge tone={state.tone}>{state.label}</Badge></td>
              {onOperate && <td><Button variant="ghost" icon={ArrowRightLeft} aria-label={`Registrar operación de ${p.nombre}`} onClick={() => onOperate(p.id)}>Operar</Button></td>}
            </tr>;
          })}
        </tbody>
      </table>
    </div>
    {!rows.length && <Empty title="Sin artículos para estos filtros" action={<Button onClick={filtro.limpiar}>Limpiar filtros</Button>} />}
    <footer className="table-footer"><span>Mostrando {rows.length} de {products.length} artículos</span><span>Un valor sin envío no equivale a cero disponible</span></footer>
  </section>;
}

const tonoMovimiento: Partial<Record<Movimiento['tipo'], 'success' | 'info'>> = { DESPACHO_DIGITAL: 'success', RESERVA_DIGITAL: 'info' };

function MovimientosDigitales({ productos, movimientos, total, cargando }: { productos: Producto[]; movimientos: Movimiento[]; total: number; cargando: boolean }) {
  return <section className="panel">
    <div className="panel-heading">
      <div><h2>Últimos movimientos digitales</h2><p>Reservas, cancelaciones y despachos confirmados · horario de Argentina.</p></div>
      <a className="text-link" href="#/compras/actividad">Ver actividad <ArrowRight size={24} aria-hidden="true" /></a>
    </div>
    <div className="table-scroll" role="region" aria-label="Movimientos digitales" tabIndex={0}>
      <table>
        <thead>
          <tr>
            <th scope="col">Movimiento</th><th scope="col">Artículo</th><th scope="col">Operación</th>
            <th scope="col" className="numeric">Cantidad</th><th scope="col">Operador y motivo</th><th scope="col">Fecha</th>
          </tr>
        </thead>
        <tbody>
          {movimientos.map(m => (
            <tr key={m.id}>
              <td>{sku(m.id)}</td>
              <td>{productos.find(p => p.id === m.stockId)?.nombre || `Artículo ${sku(m.stockId)}`}</td>
              <td><Badge tone={tonoMovimiento[m.tipo] ?? 'neutral'}>{movementLabels[m.tipo]}</Badge></td>
              <td className="numeric">{number(m.cantidad)}</td>
              <td><div className="stacked-cell digital-movement-detail"><span>{m.usuario}</span><span className="caption">{m.motivo}</span></div></td>
              <td className="nowrap">{date(m.fecha)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
    {!movimientos.length && !cargando && <Empty title="Sin movimientos digitales" text="Las operaciones confirmadas del canal aparecerán acá." />}
    <footer className="table-footer"><span>Mostrando {movimientos.length} de {total} movimientos</span><span>Del más reciente al más antiguo</span></footer>
  </section>;
}

const sinIntegraciones: Integraciones = { entregas: [], proveedoresDisponibles: { ERP: true, MENSAJERIA: true, COTO_DIGITAL: true }, mensajes: [], pedidosERP: [], catalogoDigital: {} };
const ULTIMOS_DIGITALES = 10;
/** Pestaña "Reservas y despachos" de Stock; el encabezado y las métricas los muestra StockPage. */
export function DigitalPage() {
  const { data, revision, tienePermiso } = useStore();
  const [operation, setOperation] = useState<{ id?: number } | null>(null);
  const puedeOperar = tienePermiso('operarDigital');
  // Cada recurso se pide sólo mientras la vista está abierta y se renueva cuando cambia el inventario.
  const integracionesActuales = useResource('integraciones', signal => api.integraciones(signal), revision);
  const digitales = useResource('movimientos-digitales', signal => api.movimientos({ tipos: ['RESERVA_DIGITAL', 'CANCELACION_RESERVA', 'DESPACHO_DIGITAL'], limite: ULTIMOS_DIGITALES }, signal), revision);
  if (!data) return null;
  const { productos } = data;
  const integraciones = integracionesActuales.data ?? sinIntegraciones;
  const pending = integraciones.entregas.filter(e => e.tipo === 'STOCK_DIGITAL' && e.estado === 'PENDIENTE');
  return <>
    <AvailabilityTable products={productos} integration={integraciones} pending={pending} onOperate={puedeOperar ? id => setOperation({ id }) : undefined} />
    <p className="digital-note">Las reservas se controlan por artículo. Los combos comprometen unidades de sus componentes; los totales suman únicamente artículos simples.</p>
    <MovimientosDigitales productos={productos} movimientos={digitales.data?.elementos ?? []} total={digitales.data?.total ?? 0} cargando={digitales.loading} />
    {operation && (
      <Modal title="Registrar operación digital" description="Registrá una reserva, liberá unidades o confirmá su despacho." onClose={() => setOperation(null)}>
        <div className="modal-body"><OperationForm mode="gestion-digital" initialId={operation.id} compact onComplete={() => setOperation(null)} onCancel={() => setOperation(null)} /></div>
      </Modal>
    )}
  </>;
}
