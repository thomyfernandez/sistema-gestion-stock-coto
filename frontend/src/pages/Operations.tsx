import { useState } from 'react';
import { AlertTriangle, ArrowDownToLine, ArrowRight, ArrowRightLeft, Boxes, ClipboardList, Layers3, PackageCheck, Plus } from 'lucide-react';
import { api } from '../api';
import { date, number, sku } from '../format';
import { useStore } from '../store';
import type { Pedido, Producto } from '../types';
import { InventoryTable } from '../components/InventoryTable';
import { OperationForm } from '../components/OperationForm';
import { Badge, Button, Confirmation, Empty, Field, Metric, Modal, PageHeading, ProductDetail } from '../components/ui';

export function SucursalPage({ embedded = false }: { embedded?: boolean }) {
  const { data } = useStore();
  const [detail, setDetail] = useState<Producto | null>(null);
  const [move, setMove] = useState<number | null>(null);
  if (!data) return null;
  const simples = data.productos.filter(p => p.tipo === 'SIMPLE');
  const low = simples.filter(p => data.politicas[p.id] && p.disponible < data.politicas[p.id].umbralAlerta);
  return <>{!embedded && <PageHeading eyebrow="Operación diaria" title="Inventario de sucursal" text="Cada artículo en su lugar. Todo tu stock, bajo control." actions={<Button variant="primary" icon={Plus} onClick={() => setMove(simples[0]?.id || 0)} disabled={!simples.length}>Registrar movimiento</Button>} />}
    {!embedded && <div className="metrics-grid"><Metric label="Artículos en catálogo" value={data.productos.length} hint={`${simples.length} simples · ${data.productos.length - simples.length} ${data.productos.length - simples.length === 1 ? 'combo' : 'combos'}`} icon={Boxes} /><Metric label="Disponible en depósito" value={simples.reduce((s, p) => s + p.disponible, 0)} hint="Unidades de artículos simples" icon={PackageCheck} /><Metric label="En góndola" value={simples.reduce((s, p) => s + (p.stockExhibicion || 0), 0)} hint="Listas para la venta en sucursal" icon={Layers3} /><Metric label="Reservas digitales" value={simples.reduce((s, p) => s + (p.stockReservado || 0), 0)} hint="Unidades comprometidas" icon={ClipboardList} /></div>}
    {low.length > 0 && <div className="stock-notice"><span className="notice-icon"><AlertTriangle size={24} aria-hidden="true" /></span><div><strong>{low.length === 1 ? 'Un artículo necesita atención' : `${low.length} artículos necesitan atención`}</strong><p>{low.length === 1 ? `${low[0].nombre} está por debajo del umbral de alerta.` : 'Hay artículos por debajo de su umbral de alerta.'}</p></div><a className="text-link" href="#/compras/reposicion">Revisar reposición <ArrowRight size={24} aria-hidden="true" /></a></div>}
    <InventoryTable onSelect={setDetail} /><div className="page-footnote"><span className="dot success" />Disponibilidad calculada a partir de las existencias confirmadas.</div>
    {detail && <ProductDetail product={data.productos.find(p => p.id === detail.id) || detail} onClose={() => setDetail(null)} onMove={detail.tipo === 'SIMPLE' ? () => { setMove(detail.id); setDetail(null); } : undefined} />}
    {move !== null && <Modal title="Registrar movimiento" description="Mové artículos entre depósito y góndola, o registrá un ajuste." onClose={() => setMove(null)}><div className="modal-body"><OperationForm mode="sucursal" initialId={move} compact onComplete={() => setMove(null)} onCancel={() => setMove(null)} /></div></Modal>}
  </>;
}

export function ReceiveOrder({ order, onClose }: { order: Pedido; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const [usuario, setUsuario] = useState('recepcion.demo');
  const product = data?.productos.find(p => p.id === order.stockId);
  return <Confirmation title={`Recibir pedido ${sku(order.id)}`} text={`Se sumarán ${number(order.cantidad)} unidades de ${product?.nombre || `artículo ${order.stockId}`} al depósito.`} onClose={onClose} label="Confirmar recepción"
    onConfirm={async () => { if (!usuario.trim()) throw new Error('Indicá el operador que recibe el pedido.'); return run(() => api.recibirPedido(order.id, usuario.trim()), r => `Pedido recibido. Movimiento #${r.id} confirmado.`); }}>
    <Field label="Operador"><input required value={usuario} maxLength={100} onChange={e => setUsuario(e.target.value)} disabled={busy} /></Field>
  </Confirmation>;
}

export function RecepcionPage() {
  const { data } = useStore();
  const [receiving, setReceiving] = useState<Pedido | null>(null);
  if (!data) return null;
  const ingresos = data.movimientos.filter(m => m.tipo === 'RECEPCION').slice().reverse();
  const enviados = data.pedidos.filter(p => p.estado === 'ENVIADO');
  return <><PageHeading eyebrow="Depósito" title="Recepción de mercadería" text="Registrá los ingresos y mantené las existencias al día." />
    <div className="reception-grid"><section className="panel"><div className="panel-heading"><div><h2>Ingreso directo de mercadería</h2><p>Sin pedido asociado. Aumenta el stock inmediatamente.</p></div><span className="heading-symbol"><ArrowDownToLine size={24} aria-hidden="true" /></span></div><div className="panel-body"><OperationForm mode="recepcion" /></div></section>
      <aside className="panel orders-arriving"><div className="panel-heading"><div><h2>Pedidos por recibir</h2><p>Enviados por el centro de distribución.</p></div><span className="count-tag">{enviados.length}</span></div><div className="arriving-list">{enviados.length ? enviados.map(p => <article key={p.id}><div className="split-line"><strong>Pedido {sku(p.id)}</strong><Badge tone="info">Enviado</Badge></div><p>{data.productos.find(s => s.id === p.stockId)?.nombre}</p><span className="caption">{number(p.cantidad)} unidades · {date(p.fecha)}</span><Button icon={PackageCheck} onClick={() => setReceiving(p)}>Recibir pedido</Button></article>) : <Empty title="Todo recibido" text="Los pedidos enviados por el centro de distribución aparecerán acá." />}</div></aside></div>
    <section className="panel"><div className="panel-heading"><div><h2>Últimos ingresos</h2><p>Recepciones confirmadas en la sucursal.</p></div><Badge tone="neutral">Historial</Badge></div><div className="table-scroll" tabIndex={0} role="region" aria-label="Últimos ingresos"><table><thead><tr><th scope="col">Movimiento</th><th scope="col">Artículo</th><th scope="col">Fecha</th><th scope="col">Operador</th><th scope="col" className="numeric">Unidades</th></tr></thead><tbody>{ingresos.slice(0, 8).map(m => <tr key={m.id}><td><span className="row-icon"><ArrowRightLeft size={24} aria-hidden="true" />{sku(m.id)}</span></td><td>{data.productos.find(p => p.id === m.stockId)?.nombre}</td><td className="nowrap">{date(m.fecha)}</td><td>{m.usuario}</td><td className="numeric positive">+{number(m.cantidad)}</td></tr>)}</tbody></table></div>{!ingresos.length && <Empty title="Sin ingresos registrados" text="Completá el formulario para registrar la primera recepción." />}<footer className="table-footer"><span>Mostrando {Math.min(ingresos.length, 8)} de {ingresos.length} ingresos</span><a className="text-link" href="#/compras/actividad">Ver actividad <ArrowRight size={24} aria-hidden="true" /></a></footer></section>
    {receiving && <ReceiveOrder order={receiving} onClose={() => setReceiving(null)} />}
  </>;
}
