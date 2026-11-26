import { useState } from 'react';
import { AlertTriangle, ArrowDownToLine, ArrowRight, ArrowRightLeft, PackageCheck } from 'lucide-react';
import { api } from '../api';
import { date, number, sku } from '../format';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Movimiento, Pedido, Producto } from '../types';
import { InventoryTable } from '../components/InventoryTable';
import { OperationForm } from '../components/OperationForm';
import { ComboArmadoForm } from '../components/CatalogForms';
import { Badge, Button, Confirmation, Empty, Modal, OperadorSesion, PageHeading, ProductDetail } from '../components/ui';

function AvisoStockBajo({ low }: { low: Producto[] }) {
  if (!low.length) return null;
  const titulo = low.length === 1 ? 'Un artículo necesita atención' : `${low.length} artículos necesitan atención`;
  const detalle = low.length === 1 ? `${low[0].nombre} está por debajo del umbral de alerta.` : 'Hay artículos por debajo de su umbral de alerta.';
  return <div className="stock-notice">
    <span className="notice-icon"><AlertTriangle size={24} aria-hidden="true" /></span>
    <div><strong>{titulo}</strong><p>{detalle}</p></div>
    <a className="text-link" href="#/compras/reposicion">Revisar reposición <ArrowRight size={24} aria-hidden="true" /></a>
  </div>;
}

/** Pestaña "Existencias" de Stock; el encabezado y las métricas los muestra StockPage. */
export function SucursalPage() {
  const { data, tienePermiso } = useStore();
  const [comboOperation, setComboOperation] = useState<{ combo: Producto; mode: 'armar' | 'desarmar' } | null>(null);
  const puedeOperarCombo = tienePermiso('operarCombo');
  const [detail, setDetail] = useState<Producto | null>(null);
  const [move, setMove] = useState<number | null>(null);
  if (!data) return null;
  const low = data.productos.filter(p => p.tipo === 'SIMPLE' && data.politicas[p.id] && p.disponible < data.politicas[p.id].umbralAlerta);
  const operarCombo = (mode: 'armar' | 'desarmar') => () => { if (detail) setComboOperation({ combo: detail, mode }); setDetail(null); };
  const esCombo = detail?.tipo === 'COMBO' && puedeOperarCombo;
  return <>
    <AvisoStockBajo low={low} />
    <InventoryTable onSelect={setDetail} />
    <div className="page-footnote"><span className="dot success" />Disponibilidad calculada a partir de las existencias confirmadas.</div>
    {detail && (
      <ProductDetail product={data.productos.find(p => p.id === detail.id) || detail} onClose={() => setDetail(null)}
        onMove={detail.tipo === 'SIMPLE' ? () => { setMove(detail.id); setDetail(null); } : undefined}
        onArmar={esCombo ? operarCombo('armar') : undefined}
        onDesarmar={esCombo && (detail.stockFisico || 0) > 0 ? operarCombo('desarmar') : undefined} />
    )}
    {comboOperation && <ComboArmadoForm combo={data.productos.find(p => p.id === comboOperation.combo.id) || comboOperation.combo} mode={comboOperation.mode} onClose={() => setComboOperation(null)} />}
    {move !== null && (
      <Modal title="Registrar movimiento" description="Mové artículos entre depósito y góndola, o registrá un ajuste." onClose={() => setMove(null)}>
        <div className="modal-body"><OperationForm mode="sucursal" initialId={move} compact onComplete={() => setMove(null)} onCancel={() => setMove(null)} /></div>
      </Modal>
    )}
  </>;
}

export function ReceiveOrder({ order, onClose }: { order: Pedido; onClose: () => void }) {
  const { data, run } = useStore();
  const product = data?.productos.find(p => p.id === order.stockId);
  return <Confirmation title={`Recibir pedido ${sku(order.id)}`} text={`Se sumarán ${number(order.cantidad)} unidades de ${product?.nombre || `artículo ${order.stockId}`} al depósito.`} onClose={onClose} label="Confirmar recepción"
    onConfirm={async () => run(() => api.recibirPedido(order.id), r => `Pedido recibido. Movimiento #${r.id} confirmado.`)}>
    <OperadorSesion />
  </Confirmation>;
}

function PedidoPorRecibir({ pedido, producto, onRecibir }: { pedido: Pedido; producto?: Producto; onRecibir: () => void }) {
  return <article>
    <div className="split-line"><strong>Pedido {sku(pedido.id)}</strong><Badge tone="info">Enviado</Badge></div>
    <p>{producto?.nombre}</p>
    <span className="caption">{number(pedido.cantidad)} unidades · {date(pedido.fecha)}</span>
    <Button icon={PackageCheck} onClick={onRecibir}>Recibir pedido</Button>
  </article>;
}

function TablaIngresos({ productos, ingresos }: { productos: Producto[]; ingresos: Movimiento[] }) {
  return <table>
    <thead>
      <tr><th scope="col">Movimiento</th><th scope="col">Artículo</th><th scope="col">Fecha</th><th scope="col">Operador</th><th scope="col" className="numeric">Unidades</th></tr>
    </thead>
    <tbody>
      {ingresos.map(m => (
        <tr key={m.id}>
          <td><span className="row-icon"><ArrowRightLeft size={24} aria-hidden="true" />{sku(m.id)}</span></td>
          <td>{productos.find(p => p.id === m.stockId)?.nombre}</td>
          <td className="nowrap">{date(m.fecha)}</td>
          <td>{m.usuario}</td>
          <td className="numeric positive">+{number(m.cantidad)}</td>
        </tr>
      ))}
    </tbody>
  </table>;
}

const ULTIMOS_INGRESOS = 8;
export function RecepcionPage() {
  const { data, revision } = useStore();
  const [receiving, setReceiving] = useState<Pedido | null>(null);
  const ultimos = useResource('ingresos', signal => api.movimientos({ tipos: ['RECEPCION'], limite: ULTIMOS_INGRESOS }, signal), revision);
  if (!data) return null;
  const ingresos = ultimos.data?.elementos ?? [];
  const enviados = data.pedidos.filter(p => p.estado === 'ENVIADO');
  return <>
    <PageHeading eyebrow="Depósito" title="Recepción de mercadería" text="Registrá los ingresos y mantené las existencias al día." />
    <div className="reception-grid">
      <section className="panel">
        <div className="panel-heading">
          <div><h2>Ingreso directo de mercadería</h2><p>Sin pedido asociado. Aumenta el stock inmediatamente.</p></div>
          <span className="heading-symbol"><ArrowDownToLine size={24} aria-hidden="true" /></span>
        </div>
        <div className="panel-body"><OperationForm mode="recepcion" /></div>
      </section>
      <aside className="panel orders-arriving">
        <div className="panel-heading">
          <div><h2>Pedidos por recibir</h2><p>Enviados por el centro de distribución.</p></div>
          <span className="count-tag">{enviados.length}</span>
        </div>
        <div className="arriving-list">
          {enviados.length
            ? enviados.map(p => <PedidoPorRecibir key={p.id} pedido={p} producto={data.productos.find(s => s.id === p.stockId)} onRecibir={() => setReceiving(p)} />)
            : <Empty title="Todo recibido" text="Los pedidos enviados por el centro de distribución aparecerán acá." />}
        </div>
      </aside>
    </div>
    <section className="panel">
      <div className="panel-heading">
        <div><h2>Últimos ingresos</h2><p>Recepciones confirmadas en la sucursal.</p></div>
        <Badge tone="neutral">Historial</Badge>
      </div>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="Últimos ingresos"><TablaIngresos productos={data.productos} ingresos={ingresos} /></div>
      {!ingresos.length && !ultimos.loading && <Empty title="Sin ingresos registrados" text="Completá el formulario para registrar la primera recepción." />}
      <footer className="table-footer">
        <span>Mostrando {ingresos.length} de {ultimos.data?.total ?? 0} ingresos</span>
        <a className="text-link" href="#/compras/actividad">Ver actividad <ArrowRight size={24} aria-hidden="true" /></a>
      </footer>
    </section>
    {receiving && <ReceiveOrder order={receiving} onClose={() => setReceiving(null)} />}
  </>;
}
