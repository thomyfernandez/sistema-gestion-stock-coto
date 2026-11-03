import { useState } from 'react';
import { Activity, ArrowRight, CheckCheck, ClipboardList, Clock3, Edit3, PackagePlus, Plus, RefreshCw, Settings2, Truck } from 'lucide-react';
import { api } from '../api';
import { date, money, movementLabels, number, sku, stockStatus } from '../format';
import { resolverDestinatariosAviso } from '../notifications';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Pedido, Producto, Promocion } from '../types';
import { InventoryTable } from '../components/InventoryTable';
import { PolicyForm, ProductForm, PromotionForm } from '../components/CatalogForms';
import { Badge, Button, Confirmation, Empty, ErrorMessage, Field, Metric, PageHeading, ProductDetail, ProductName } from '../components/ui';
import { ReceiveOrder } from './Operations';

const tabs = [['reposicion', 'Reposición'], ['catalogo', 'Productos y combos'], ['promociones', 'Promociones'], ['actividad', 'Actividad']];
const orderTones = { PENDIENTE: 'warning', ENVIADO: 'info', RECIBIDO: 'success', CANCELADO: 'neutral' } as const;
const orderLabels = { PENDIENTE: 'Pendiente', ENVIADO: 'Enviado', RECIBIDO: 'Recibido', CANCELADO: 'Cancelado' };

function Replenishment() {
  const { data, busy, act, run } = useStore();
  const [receiving, setReceiving] = useState<Pedido | null>(null);
  const [canceling, setCanceling] = useState<Pedido | null>(null);
  const [policy, setPolicy] = useState<Producto | null>(null);
  const [estado, setEstado] = useState('TODOS');
  if (!data) return null;
  const pedidos = data.pedidos.filter(p => estado === 'TODOS' || p.estado === estado).slice().reverse();
  return <><div className="metrics-grid three"><Metric label="Pendientes de envío" value={data.pedidos.filter(p => p.estado === 'PENDIENTE').length} hint="Solicitudes al centro de distribución" icon={Clock3} /><Metric label="En camino" value={data.pedidos.filter(p => p.estado === 'ENVIADO').length} hint="Listos para recibir en depósito" icon={Truck} /><Metric label="Recepciones completas" value={data.pedidos.filter(p => p.estado === 'RECIBIDO').length} hint="Mercadería incorporada al stock" icon={CheckCheck} /></div>
    <section className="panel"><div className="panel-heading"><div><h2>Pedidos de reposición</h2><p>Reposición desde el centro de distribución según la política de cada artículo.</p></div><Button variant="primary" icon={RefreshCw} loading={busy} onClick={() => void act(api.evaluar, list => `Evaluación completada · ${list.length} ${list.length === 1 ? 'pedido generado' : 'pedidos generados'}.`)}>Evaluar reposición</Button></div>
      <div className="table-toolbar"><span className="caption">Se descuentan los pedidos pendientes y enviados.</span><select aria-label="Estado de los pedidos" value={estado} onChange={e => setEstado(e.target.value)}><option value="TODOS">Todos los estados</option>{Object.entries(orderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="Pedidos de reposición"><table><thead><tr><th scope="col">Pedido</th><th scope="col">Artículo</th><th scope="col" className="numeric">Unidades</th><th scope="col">Fecha</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead><tbody>{pedidos.map(p => <tr key={p.id}><td><strong>{sku(p.id)}</strong></td><td>{data.productos.find(s => s.id === p.stockId)?.nombre || `Artículo ${p.stockId}`}</td><td className="numeric">{number(p.cantidad)}</td><td className="nowrap">{date(p.fecha)}</td><td><Badge tone={orderTones[p.estado]}>{orderLabels[p.estado]}</Badge></td><td>{p.estado === 'ENVIADO' ? <Button icon={PackagePlus} onClick={() => setReceiving(p)} disabled={busy}>Recibir</Button> : p.estado === 'PENDIENTE' ? <Button variant="ghost" onClick={() => setCanceling(p)} disabled={busy}>Cancelar</Button> : <span className="caption">Completado</span>}</td></tr>)}</tbody></table></div>{!pedidos.length && <Empty title="Sin pedidos para mostrar" text="Evaluá la reposición o seleccioná otro estado." />}</section>
    <section className="panel"><div className="panel-heading"><div><h2>Políticas por artículo</h2><p>Definí cómo se anticipa la reposición y cuándo se envía un aviso.</p></div><Settings2 size={24} aria-hidden="true" /></div><div className="table-scroll" tabIndex={0} role="region" aria-label="Políticas de reposición"><table><thead><tr><th scope="col">Artículo</th><th scope="col">Política</th><th scope="col" className="numeric">Disponible</th><th scope="col">Estado</th><th scope="col">Avisar a</th><th scope="col"><span className="sr-only">Configuración</span></th></tr></thead><tbody>{data.productos.filter(p => p.tipo === 'SIMPLE').map(p => { const config = data.politicas[p.id]; const status = stockStatus(p, config); const notice = resolverDestinatariosAviso(data, p); return <tr key={p.id}><td><ProductName product={p} /></td><td>{config ? ({ PUNTO_PEDIDO: 'Punto de pedido', DEMANDA: 'Por demanda', ESTACIONAL: 'Estacional' })[config.tipo] : 'Sin configurar'}</td><td className="numeric available">{number(p.disponible)}</td><td><Badge tone={status.tone}>{status.label}</Badge></td><td>{!config ? <span className="caption">—</span> : notice.emails.length === 0 ? <Badge tone="warning">Sin destinatarios</Badge> : <div tabIndex={0} title={notice.emails.join(', ')} aria-label={`Destinatarios resueltos: ${notice.emails.join(', ')}`} style={{ outlineOffset: '2px', cursor: 'help' }}><strong>{notice.emails.length} {notice.emails.length === 1 ? 'email' : 'emails'}</strong><span className="caption block">{notice.origen === 'CATEGORIA' ? `Categoría: ${notice.categoriaNombre || 'Sector'}` : 'Lista general'}</span></div>}</td><td><Button variant="ghost" icon={Edit3} onClick={() => setPolicy(p)} aria-label={`Configurar política de ${p.nombre}`}>Configurar</Button></td></tr>; })}</tbody></table></div></section>
    {receiving && <ReceiveOrder order={receiving} onClose={() => setReceiving(null)} />}{policy && <PolicyForm product={policy} onClose={() => setPolicy(null)} />}{canceling && <Confirmation title={`Cancelar pedido ${sku(canceling.id)}`} text="El pedido dejará de contar como mercadería pendiente de recibir. Esta acción no modifica el stock físico." danger label="Cancelar pedido" onClose={() => setCanceling(null)} onConfirm={() => run(() => api.cancelarPedido(canceling.id), 'Pedido cancelado.')} />}
  </>;
}

function PriceSimulation({ productId, cantidad, medioPago }: { productId: number; cantidad: number; medioPago: string }) {
  const { revision } = useStore();
  const quote = useResource(`simulacion:${productId}:${cantidad}:${medioPago}:${revision}`, signal => api.precio(productId, cantidad, medioPago, signal));
  return <div className="quote-box" aria-live="polite">{quote.loading ? <p>Calculando precio…</p> : quote.error ? <ErrorMessage error={quote.error} /> : quote.data && <><div className="split-line"><span>Precio de lista</span><span>{money(quote.data.precioLista)}</span></div><div className="split-line positive"><span>Ahorro total</span><strong>-{money(quote.data.ahorro)}</strong></div><div className="split-line quote-total"><span>Total final</span><strong>{money(quote.data.precioFinal)}</strong></div><span className="caption">{quote.data.promocionesAplicadas.length} {quote.data.promocionesAplicadas.length === 1 ? 'promoción aplicada' : 'promociones aplicadas'} · importe por {cantidad} {cantidad === 1 ? 'unidad' : 'unidades'}</span></>}</div>;
}

function Promotions() {
  const { data, revision, busy, act } = useStore();
  const [productId, setProductId] = useState(data?.productos[0]?.id || 0);
  const [editing, setEditing] = useState<Promocion | 'new' | null>(null);
  const [cantidad, setCantidad] = useState('3');
  const [medioPago, setMedioPago] = useState('DEBITO');
  const promotions = useResource(`promociones:${productId}:${revision}`, signal => api.promociones(productId, signal));
  if (!data) return null;
  const product = data.productos.find(p => p.id === productId);
  return <section className="panel"><div className="panel-heading"><div><h2>Promociones acumulables</h2><p>Se aplican sobre el precio de lista en el orden configurado.</p></div>{product && <Button variant="primary" icon={Plus} disabled={busy} onClick={() => setEditing('new')}>Nueva promoción</Button>}</div>
    <div className="table-toolbar"><select aria-label="Artículo de promociones" value={productId} onChange={e => setProductId(Number(e.target.value))}>{data.productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}</select></div>
    {promotions.loading ? <p>Cargando promociones…</p> : promotions.error ? <ErrorMessage error={promotions.error} /> : promotions.data && <>
      {promotions.data.length ? <div className="table-scroll" tabIndex={0} role="region" aria-label="Promociones del artículo"><table><thead><tr><th scope="col" className="numeric">Orden</th><th scope="col">Tipo</th><th scope="col" className="numeric">Beneficio</th><th scope="col">Condición</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Acciones</span></th></tr></thead><tbody>{promotions.data.map(p => <tr key={p.id}><td className="numeric">{p.orden}</td><td>{{ DESCUENTO: 'Descuento porcentual', DOS_POR_UNO: '2x1 en unidades', MEDIO_PAGO: 'Medio de pago' }[p.tipo]}</td><td className="numeric">{p.tipo === 'DOS_POR_UNO' ? '50 % en la 2.ª unidad' : `${p.porcentaje} %`}</td><td>{p.medioPago ? `Abonando con ${p.medioPago}` : 'Sin condición'}</td><td><Badge tone={p.activa ? 'success' : 'neutral'}>{p.activa ? 'Activa' : 'Inactiva'}</Badge></td><td><Button variant="ghost" icon={Edit3} disabled={busy} onClick={() => setEditing(p)} aria-label={`Editar promoción ${p.orden}`}>Editar</Button></td></tr>)}</tbody></table></div> : <Empty title="Sin promociones para este artículo" text="Creá descuentos, 2x1 o beneficios por medio de pago." />}
    </>}
    <div className="promo-simulation"><div className="split-line"><h3>Simulador de valorización</h3><span className="caption">Calcula los descuentos en cascada</span></div><div className="form-grid"><Field label="Cantidad a simular"><input type="number" min="1" max="999" value={cantidad} onChange={e => setCantidad(e.target.value)} /></Field><Field label="Medio de pago"><select value={medioPago} onChange={e => setMedioPago(e.target.value)}><option value="EFECTIVO">Efectivo</option><option value="DEBITO">Tarjeta de débito</option><option value="CREDITO">Tarjeta de crédito</option><option value="MERCADOPAGO">Mercado Pago</option></select></Field></div>{product && <PriceSimulation productId={product.id} cantidad={Math.max(1, Number(cantidad) || 1)} medioPago={medioPago} />}</div>
    {editing && product && <PromotionForm product={product} existing={editing === 'new' ? undefined : editing} nextOrder={(promotions.data?.length || 0) + 1} onClose={() => setEditing(null)} />}
  </section>;
}

function ActivityPanel() {
  const { data } = useStore();
  const [stockId, setStockId] = useState('TODOS');
  const [mode, setMode] = useState('movimientos');
  if (!data) return null;
  const movements = data.movimientos.filter(m => stockId === 'TODOS' || m.stockId === Number(stockId)).slice().reverse();
  const audits = data.auditoria.filter(a => stockId === 'TODOS' || a.stockId === Number(stockId)).slice().reverse();
  return <section className="panel"><div className="panel-heading"><div><h2>Actividad del inventario</h2><p>Movimientos y cambios de disponibilidad · horario de Argentina.</p></div><Activity size={24} aria-hidden="true" /></div><div className="table-toolbar"><div className="segmented" role="group" aria-label="Tipo de actividad"><button className={mode === 'movimientos' ? 'active' : ''} aria-pressed={mode === 'movimientos'} onClick={() => setMode('movimientos')}>Movimientos</button><button className={mode === 'auditoria' ? 'active' : ''} aria-pressed={mode === 'auditoria'} onClick={() => setMode('auditoria')}>Auditoría</button></div><select aria-label="Artículo de actividad" value={stockId} onChange={e => setStockId(e.target.value)}><option value="TODOS">Todos los artículos</option>{data.productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}</select></div>
    <div className="table-scroll" tabIndex={0} role="region" aria-label={mode === 'movimientos' ? 'Movimientos del inventario' : 'Auditoría del stock'}>{mode === 'movimientos' ? <table><thead><tr><th scope="col">Movimiento</th><th scope="col">Artículo</th><th scope="col">Operación</th><th scope="col" className="numeric">Cantidad</th><th scope="col">Operador y motivo</th><th scope="col">Fecha</th></tr></thead><tbody>{movements.map(m => <tr key={m.id}><td>{sku(m.id)}</td><td>{data.productos.find(p => p.id === m.stockId)?.nombre}</td><td><span className="nowrap">{movementLabels[m.tipo]}</span></td><td className="numeric">{number(m.cantidad)}</td><td><div className="stacked-cell"><span>{m.usuario}</span><span className="caption">{m.motivo}</span></div></td><td className="nowrap">{date(m.fecha)}</td></tr>)}</tbody></table> : <table><thead><tr><th scope="col">Artículo</th><th scope="col" className="numeric">Antes</th><th scope="col" className="numeric">Después</th><th scope="col">Detalle</th><th scope="col">Fecha</th></tr></thead><tbody>{audits.map((a, i) => <tr key={`${a.stockId}:${a.fecha}:${i}`}><td>{data.productos.find(p => p.id === a.stockId)?.nombre}</td><td className="numeric">{a.disponibleAnterior === null ? '—' : number(a.disponibleAnterior)}</td><td className="numeric available">{number(a.disponibleActual)}</td><td>{a.detalle}</td><td className="nowrap">{date(a.fecha)}</td></tr>)}</tbody></table>}</div>
    {(mode === 'movimientos' ? movements : audits).length === 0 && <Empty title="Sin actividad" text="Los cambios confirmados del artículo aparecerán acá." />}<footer className="table-footer"><span>{(mode === 'movimientos' ? movements : audits).length} registros</span><span>Ordenados del más reciente al más antiguo</span></footer></section>;
}

export function PurchasesPage({ tab }: { tab: string }) {
  const { data } = useStore();
  const [create, setCreate] = useState<'product' | 'combo' | null>(null);
  const [detail, setDetail] = useState<Producto | null>(null);
  const active = tabs.some(([id]) => id === tab) ? tab : 'reposicion';
  return <><PageHeading eyebrow="Backoffice" title="Compras y abastecimiento" text="Anticipá la reposición y administrá el catálogo de la sucursal." actions={active === 'catalogo' && <><Button icon={Plus} onClick={() => setCreate('combo')}>Nuevo combo</Button><Button variant="primary" icon={Plus} onClick={() => setCreate('product')}>Nuevo artículo</Button></>} />
    <nav className="tabs" aria-label="Secciones de compras">{tabs.map(([id, label]) => <a key={id} href={`#/compras/${id}`} className={active === id ? 'active' : ''} aria-current={active === id ? 'page' : undefined}>{label}{id === 'reposicion' && !!data?.pedidos.filter(p => p.estado === 'PENDIENTE' || p.estado === 'ENVIADO').length && <span className="tab-count">{data.pedidos.filter(p => p.estado === 'PENDIENTE' || p.estado === 'ENVIADO').length}</span>}</a>)}</nav>
    {active === 'reposicion' && <Replenishment />}{active === 'catalogo' && <InventoryTable onSelect={setDetail} />}{active === 'promociones' && <Promotions />}{active === 'actividad' && <ActivityPanel />}
    {create && <ProductForm combo={create === 'combo'} onClose={() => setCreate(null)} />}{detail && <ProductDetail product={data?.productos.find(p => p.id === detail.id) || detail} onClose={() => setDetail(null)} />}
    {active === 'reposicion' && <div className="page-footnote"><ClipboardList size={24} aria-hidden="true" />Los pedidos recibidos se incorporan al depósito en una única operación.<a href="#/recepcion" className="text-link">Ir a recepción <ArrowRight size={24} aria-hidden="true" /></a></div>}
  </>;
}
