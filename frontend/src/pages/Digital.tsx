import { useState, type FormEvent } from 'react';
import { ArrowRight, BadgePercent, Layers3, Minus, Package, Plus, Search, ShoppingBag, Sparkles } from 'lucide-react';
import { api } from '../api';
import { money, number } from '../format';
import { useStore } from '../store';
import type { ItemCatalogo } from '../types';
import { useResource } from '../useResource';
import { OperationForm } from '../components/OperationForm';
import { Badge, Button, Empty, ErrorMessage, Field, Modal, PageHeading } from '../components/ui';

function ReserveForm({ item, medioPago, onClose }: { item: ItemCatalogo; medioPago: string; onClose: () => void }) {
  const { data, revision, busy, error: connectionError, run } = useStore();
  const [cantidad, setCantidad] = useState(1);
  const [usuario, setUsuario] = useState('digital.demo');
  const [motivo, setMotivo] = useState('Compra por Coto Digital');
  const [error, setError] = useState<Error | null>(null);
  const actual = data?.productos.find(p => p.id === item.id);
  const max = actual?.disponible ?? item.disponible;
  const price = useResource(`reserva:${item.id}:${cantidad}:${medioPago}:${revision}`, signal => api.precio(item.id, Math.max(1, cantidad), medioPago, signal));
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.digital('reservas', { stockId: item.id, cantidad, usuario: usuario.trim(), motivo: motivo.trim() }), r => `Reserva confirmada · movimiento #${r.id}.`); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return <Modal title="Reservar artículo" description="Las unidades se descuentan del disponible al confirmar." onClose={onClose}><form onSubmit={submit}><div className="modal-body form-stack"><div className="reserve-product"><span className="product-mark large">{item.tipo === 'COMBO' ? <Layers3 size={32} /> : <Package size={32} />}</span><div><h3>{item.nombre}</h3><span className="caption">{number(max)} disponibles</span></div></div>
    <fieldset className="field"><legend>Cantidad</legend><div className="quantity-control"><Button variant="ghost" icon={Minus} className="icon-button" aria-label="Quitar una unidad" disabled={busy || cantidad <= 1} onClick={() => setCantidad(v => v - 1)} /><input aria-label="Cantidad a reservar" type="number" min="1" max={max} step="1" required value={cantidad || ''} onChange={e => setCantidad(Number(e.target.value))} disabled={busy} /><Button variant="ghost" icon={Plus} className="icon-button" aria-label="Agregar una unidad" disabled={busy || cantidad >= max} onClick={() => setCantidad(v => v + 1)} /></div></fieldset>
    <div className="quote-box" aria-live="polite">{price.loading ? <p className="muted">Calculando precio…</p> : price.error ? <ErrorMessage error={price.error} /> : price.data && <><div className="split-line"><span>Precio de lista</span><span>{money(price.data.precioLista)}</span></div><div className="split-line positive"><span>Ahorro por promociones</span><span>−{money(price.data.ahorro)}</span></div><div className="split-line quote-total"><strong>Total de la línea</strong><strong>{money(price.data.precioFinal)}</strong></div><span className="caption">Medio para promociones: {medioPago}. La reserva no realiza un cobro.</span></>}</div>
    {actual?.tipo === 'COMBO' && <div className="component-list">{actual.componentes.map(c => <div key={c.stockId}><span>{c.nombre}</span><strong>× {c.cantidadEnCombo * Math.max(0, cantidad)}</strong></div>)}</div>}
    <Field label="Motivo"><input required maxLength={500} value={motivo} onChange={e => setMotivo(e.target.value)} disabled={busy} /></Field><Field label="Operador"><input required maxLength={100} value={usuario} onChange={e => setUsuario(e.target.value)} disabled={busy} /></Field><ErrorMessage error={error} /></div>
    <footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button type="submit" variant="primary" icon={ShoppingBag} loading={busy} disabled={max === 0 || !cantidad || !!connectionError || price.loading || !!price.error}>Confirmar reserva</Button></footer></form></Modal>;
}

export function DigitalPage() {
  const { revision } = useStore();
  const [medioPago, setMedioPago] = useState('DEBITO');
  const [search, setSearch] = useState('');
  const [tipo, setTipo] = useState('TODOS');
  const [selected, setSelected] = useState<ItemCatalogo | null>(null);
  const [manage, setManage] = useState(false);
  const catalog = useResource(`catalogo:${medioPago}:${revision}`, signal => api.catalogo(medioPago, signal));
  const items = catalog.data?.filter(p => p.nombre.toLocaleLowerCase('es').includes(search.toLocaleLowerCase('es')) && (tipo === 'TODOS' || p.tipo === tipo)) || [];
  return <><PageHeading eyebrow="Canal online" title="Coto Digital" text="El catálogo de la sucursal, listo para tus compras online." actions={<Button icon={ShoppingBag} onClick={() => setManage(true)}>Gestionar reservas</Button>} />
    <div className="digital-banner"><div><span className="eyebrow">Más juntos, más ahorro</span><h2>Combos que rinden más.</h2><p>Promociones acumulables y disponibilidad actualizada.</p><button className="text-link" onClick={() => setTipo('COMBO')}>Explorar combos <ArrowRight size={24} aria-hidden="true" /></button></div><div className="banner-illustration" aria-hidden="true"><span className="illustration-package"><Package size={48} /></span><span className="illustration-badge"><BadgePercent size={48} /></span><span className="illustration-spark"><Sparkles size={32} /></span></div></div>
    <div className="catalog-toolbar"><div className="search-field"><Search size={24} aria-hidden="true" /><input aria-label="Buscar en catálogo digital" placeholder="¿Qué artículo estás buscando?" value={search} onChange={e => setSearch(e.target.value)} /></div><label className="inline-label">Promociones con<select value={medioPago} onChange={e => setMedioPago(e.target.value)}><option value="DEBITO">Débito</option><option value="CREDITO">Crédito</option><option value="EFECTIVO">Efectivo</option></select></label></div>
    <div className="catalog-tabs" role="group" aria-label="Tipo de catálogo">{[['TODOS', 'Todos los artículos'], ['SIMPLE', 'Artículos simples'], ['COMBO', 'Combos']].map(([value, label]) => <button key={value} className={tipo === value ? 'active' : ''} aria-pressed={tipo === value} onClick={() => setTipo(value)}>{label}</button>)}<span>{items.length} resultados</span></div>
    <ErrorMessage error={catalog.error} />{catalog.loading ? <div className="catalog-grid" aria-label="Cargando catálogo" aria-busy="true">{Array.from({ length: 6 }, (_, i) => <div key={i} className="catalog-card skeleton-card"><div className="skeleton block" /><div className="skeleton line" /><div className="skeleton line short" /></div>)}</div> : <div className="catalog-grid">{items.map(item => <article className="catalog-card" key={item.id}><div className={`catalog-art ${item.tipo === 'COMBO' ? 'combo' : ''}`}><span className="catalog-symbol">{item.tipo === 'COMBO' ? <Layers3 size={48} aria-hidden="true" /> : <Package size={48} aria-hidden="true" />}</span>{item.precio.ahorro > 0 && <Badge tone="primary">Con promoción</Badge>}{item.tipo === 'COMBO' && <span className="catalog-type">Combo ahorro</span>}</div><div className="catalog-content"><span className="caption">{item.tipo === 'COMBO' ? 'Combos y canastas' : 'Selección de la sucursal'}</span><h3>{item.nombre}</h3><p>{item.descripcion}</p><div className="catalog-price">{item.precio.ahorro > 0 && <del>{money(item.precio.precioLista)}</del>}<strong>{money(item.precio.precioFinal)}</strong><span className="caption">Por una unidad · promociones con {medioPago.toLocaleLowerCase('es')}</span></div><div className="catalog-bottom"><Badge tone={item.disponible ? 'success' : 'danger'}>{item.disponible ? `${number(item.disponible)} disponibles` : 'Sin disponible'}</Badge><Button variant="primary" onClick={() => setSelected(item)} disabled={item.disponible === 0} aria-label={`Reservar ${item.nombre}`}>Reservar</Button></div></div></article>)}</div>}
    {!catalog.loading && !catalog.error && !items.length && <Empty action={<Button onClick={() => { setSearch(''); setTipo('TODOS'); }}>Ver todo el catálogo</Button>} />}
    {selected && <ReserveForm item={selected} medioPago={medioPago} onClose={() => setSelected(null)} />}{manage && <Modal title="Gestionar reservas" description="Liberá unidades reservadas o registrá su despacho." onClose={() => setManage(false)}><div className="modal-body"><OperationForm mode="gestion-digital" compact onComplete={() => setManage(false)} onCancel={() => setManage(false)} /></div></Modal>}
  </>;
}
