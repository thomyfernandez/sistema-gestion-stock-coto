import { useState } from 'react';
import { ArrowRight, ArrowRightLeft, Boxes, ClipboardList, Plus, RefreshCw, Search, Truck } from 'lucide-react';
import { date, movementLabels, number, sku } from '../format';
import { useStore } from '../store';
import type { Evento, Integraciones, Producto } from '../types';
import { OperationForm } from '../components/OperationForm';
import { Badge, Button, Empty, Metric, Modal, PageHeading } from '../components/ui';

function syncStatus(product: Producto, published: string | undefined, pending: boolean) {
  if (pending) return { key: 'PENDIENTE', label: 'Pendiente', tone: 'warning' as const };
  if (published === undefined) return { key: 'SIN_ENVIO', label: 'Sin envío', tone: 'neutral' as const };
  if (Number(published) !== product.disponible) return { key: 'DESACTUALIZADO', label: 'Desactualizado', tone: 'danger' as const };
  return { key: 'SINCRONIZADO', label: 'Sincronizado', tone: 'success' as const };
}

function AvailabilityTable({ products, integration, pending, onOperate }: {
  products: Producto[]; integration: Integraciones; pending: Evento[]; onOperate: (id: number) => void;
}) {
  const { data } = useStore();
  const [category, setCategory] = useState('TODAS');
  const [search, setSearch] = useState('');
  const [type, setType] = useState('TODOS');
  const [status, setStatus] = useState('TODOS');
  const pendingIds = new Set(pending.map(e => e.stockId));
  const rows = products.filter(p => `${p.nombre} ${p.id} ${p.descripcion}`.toLocaleLowerCase('es').includes(search.toLocaleLowerCase('es'))
    && (category === 'TODAS' || (category === 'SIN_CATEGORIA' ? p.categoriaId === null : p.categoriaId === Number(category)))
    && (type === 'TODOS' || p.tipo === type || (type === 'RESERVADOS' && p.tipo === 'SIMPLE' && (p.stockReservado || 0) > 0))
    && (status === 'TODOS' || syncStatus(p, integration.catalogoDigital[p.id], pendingIds.has(p.id)).key === status));
  return <section className="panel digital-stock"><div className="panel-heading"><div><h2>Stock del canal digital</h2><p>Compará las existencias locales con el último valor publicado.</p></div><span className="count-tag">{products.length} artículos</span></div>
    <div className="table-toolbar"><div className="search-field"><Search size={24} aria-hidden="true" /><input aria-label="Buscar stock digital" placeholder="Buscar por nombre o código…" value={search} onChange={e => setSearch(e.target.value)} /></div>
      <div className="filter-group"><select aria-label="Filtrar stock digital por categoría" value={category} onChange={e => setCategory(e.target.value)}><option value="TODAS">Todas las categorías</option><option value="SIN_CATEGORIA">Sin categoría</option>{data?.categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}</select><select aria-label="Filtrar stock digital" value={type} onChange={e => setType(e.target.value)}><option value="TODOS">Todos los artículos</option><option value="RESERVADOS">Simples con reservas</option><option value="SIMPLE">Artículos simples</option><option value="COMBO">Combos</option></select>
        <select aria-label="Estado de sincronización" value={status} onChange={e => setStatus(e.target.value)}><option value="TODOS">Todos los estados</option><option value="PENDIENTE">Pendiente</option><option value="SINCRONIZADO">Sincronizado</option><option value="DESACTUALIZADO">Desactualizado</option><option value="SIN_ENVIO">Sin envío</option></select></div></div>
    <div className="table-scroll" role="region" aria-label="Stock y reservas digitales" tabIndex={0}><table><thead><tr><th scope="col">Artículo</th><th scope="col" className="numeric">Reservado</th><th scope="col" className="numeric">Disponible local</th><th scope="col" className="numeric">Publicado online</th><th scope="col">Sincronización</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead>
      <tbody>{rows.map(p => {
        const published = integration.catalogoDigital[p.id];
        const state = syncStatus(p, published, pendingIds.has(p.id));
        return <tr key={p.id}><td><div className="stacked-cell"><strong>{p.nombre}</strong><span className="caption">{sku(p.id)} · {p.tipo === 'COMBO' ? 'Combo · reservas en sus componentes' : 'Artículo simple'}</span></div></td>
          <td className="numeric">{p.stockReservado === null ? '—' : number(p.stockReservado)}</td><td className="numeric available">{number(p.disponible)}</td><td className="numeric">{published === undefined ? '—' : number(Number(published))}</td><td><Badge tone={state.tone}>{state.label}</Badge></td>
          <td><Button variant="ghost" icon={ArrowRightLeft} aria-label={`Registrar operación de ${p.nombre}`} onClick={() => onOperate(p.id)}>Operar</Button></td></tr>;
      })}</tbody></table></div>
    {!rows.length && <Empty title="Sin artículos para estos filtros" action={<Button onClick={() => { setSearch(''); setCategory('TODAS'); setType('TODOS'); setStatus('TODOS'); }}>Limpiar filtros</Button>} />}
    <footer className="table-footer"><span>Mostrando {rows.length} de {products.length} artículos</span><span>Un valor sin envío no equivale a cero disponible</span></footer>
  </section>;
}

export function DigitalPage({ embedded = false }: { embedded?: boolean }) {
  const { data, error } = useStore();
  const [operation, setOperation] = useState<{ id?: number } | null>(null);
  if (!data) return null;
  const { productos, integraciones } = data;
  const simples = productos.filter(p => p.tipo === 'SIMPLE');
  const pending = integraciones.entregas.filter(e => e.tipo === 'STOCK_DIGITAL' && e.estado === 'PENDIENTE');
  const movements = data.movimientos.filter(m => ['RESERVA_DIGITAL', 'CANCELACION_RESERVA', 'DESPACHO_DIGITAL'].includes(m.tipo))
    .sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id);
  return <>
    {!embedded && <PageHeading eyebrow="Control de stock · Coto Digital" title="Operaciones digitales" text="Gestioná las reservas y el despacho de mercadería del canal online."
      actions={<Button variant="primary" icon={Plus} disabled={!!error || !productos.length} onClick={() => setOperation({})}>Registrar operación</Button>} />}
    {!embedded && <div className="metrics-grid">
      <Metric label="Unidades reservadas" value={simples.reduce((total, p) => total + (p.stockReservado || 0), 0)} hint="Comprometidas en artículos simples" icon={ClipboardList} />
      <Metric label="Disponible para reservar" value={simples.reduce((total, p) => total + p.disponible, 0)} hint="Unidades simples en depósito" icon={Boxes} />
      <Metric label="Sincronizaciones pendientes" value={pending.length} hint="Actualizaciones de disponibilidad" icon={RefreshCw} tone={pending.length ? 'warning' : ''} />
      <Metric label="Despachos registrados" value={movements.filter(m => m.tipo === 'DESPACHO_DIGITAL').length} hint="Movimientos confirmados del canal" icon={Truck} />
    </div>}
    <AvailabilityTable products={productos} integration={integraciones} pending={pending} onOperate={id => setOperation({ id })} />
    <p className="digital-note">Las reservas se controlan por artículo. Los combos comprometen unidades de sus componentes; los totales suman únicamente artículos simples.</p>
    <section className="panel"><div className="panel-heading"><div><h2>Últimos movimientos digitales</h2><p>Reservas, cancelaciones y despachos confirmados · horario de Argentina.</p></div><a className="text-link" href="#/compras/actividad">Ver actividad <ArrowRight size={24} aria-hidden="true" /></a></div>
      <div className="table-scroll" role="region" aria-label="Movimientos digitales" tabIndex={0}><table><thead><tr><th scope="col">Movimiento</th><th scope="col">Artículo</th><th scope="col">Operación</th><th scope="col" className="numeric">Cantidad</th><th scope="col">Operador y motivo</th><th scope="col">Fecha</th></tr></thead><tbody>{movements.slice(0, 10).map(m => <tr key={m.id}><td>{sku(m.id)}</td><td>{productos.find(p => p.id === m.stockId)?.nombre || `Artículo ${sku(m.stockId)}`}</td><td><Badge tone={m.tipo === 'DESPACHO_DIGITAL' ? 'success' : m.tipo === 'RESERVA_DIGITAL' ? 'info' : 'neutral'}>{movementLabels[m.tipo]}</Badge></td><td className="numeric">{number(m.cantidad)}</td><td><div className="stacked-cell digital-movement-detail"><span>{m.usuario}</span><span className="caption">{m.motivo}</span></div></td><td className="nowrap">{date(m.fecha)}</td></tr>)}</tbody></table></div>
      {!movements.length && <Empty title="Sin movimientos digitales" text="Las operaciones confirmadas del canal aparecerán acá." />}
      <footer className="table-footer"><span>Mostrando {Math.min(10, movements.length)} de {movements.length} movimientos</span><span>Del más reciente al más antiguo</span></footer>
    </section>
    {operation && <Modal title="Registrar operación digital" description="Registrá una reserva, liberá unidades o confirmá su despacho." onClose={() => setOperation(null)}><div className="modal-body"><OperationForm mode="gestion-digital" initialId={operation.id} compact onComplete={() => setOperation(null)} onCancel={() => setOperation(null)} /></div></Modal>}
  </>;
}
