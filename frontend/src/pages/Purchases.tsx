import { useState } from 'react';
import { Activity, ArrowRight, CheckCheck, ChevronLeft, ChevronRight, ClipboardList, Clock3, Edit3, PackagePlus, Plus, RefreshCw, Settings2, Truck } from 'lucide-react';
import { api } from '../api';
import { date, movementLabels, number, sku, stockStatus } from '../format';
import { destinoAviso } from '../notifications';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Auditoria, Movimiento, Pedido, Politica, Producto, Snapshot } from '../types';
import { InventoryTable } from '../components/InventoryTable';
import { PolicyForm, ProductForm } from '../components/CatalogForms';
import { PanelPromociones } from '../components/Promociones';
import { Badge, Button, Confirmation, Empty, ErrorMessage, Metric, PageHeading, ProductDetail, ProductName } from '../components/ui';
import { ReceiveOrder } from './Operations';
import { SeasonsPanel } from './Seasons';

const tabs = [['reposicion', 'Reposición'], ['temporadas', 'Temporadas'], ['catalogo', 'Productos y combos'], ['promociones', 'Promociones'], ['actividad', 'Actividad']];
const orderTones = { PENDIENTE: 'warning', ENVIADO: 'info', RECIBIDO: 'success', CANCELADO: 'neutral' } as const;
const orderLabels = { PENDIENTE: 'Pendiente', ENVIADO: 'Enviado', RECIBIDO: 'Recibido', CANCELADO: 'Cancelado' };
const policyLabels: Record<Politica['tipo'], string> = { PUNTO_PEDIDO: 'Punto de pedido', DEMANDA: 'Por demanda', ESTACIONAL: 'Estacional' };

function AccionPedido({ pedido, onRecibir, onCancelar }: { pedido: Pedido; onRecibir: () => void; onCancelar: () => void }) {
  const { busy } = useStore();
  if (pedido.estado === 'ENVIADO') return <Button icon={PackagePlus} onClick={onRecibir} disabled={busy}>Recibir</Button>;
  if (pedido.estado === 'PENDIENTE') return <Button variant="ghost" onClick={onCancelar} disabled={busy}>Cancelar</Button>;
  return <span className="caption">Completado</span>;
}

function FilaPolitica({ data, product, onConfigurar }: { data: Snapshot; product: Producto; onConfigurar: () => void }) {
  const config = data.politicas[product.id];
  const status = stockStatus(product, config);
  const notice = destinoAviso(data, product);
  return (
    <tr>
      <td><ProductName product={product} /></td>
      <td>{config ? policyLabels[config.tipo] : 'Sin configurar'}</td>
      <td className="numeric available">{number(product.disponible)}</td>
      <td><Badge tone={status.tone}>{status.label}</Badge></td>
      <td>
        {config ? notice.nombre : '—'}
        {config && <span className="caption block">{notice.canal} · {notice.origen}</span>}
      </td>
      <td><Button variant="ghost" icon={Edit3} onClick={onConfigurar} aria-label={`Configurar política de ${product.nombre}`}>Configurar</Button></td>
    </tr>
  );
}

function Replenishment() {
  const { data, busy, act, run } = useStore();
  const [receiving, setReceiving] = useState<Pedido | null>(null);
  const [canceling, setCanceling] = useState<Pedido | null>(null);
  const [policy, setPolicy] = useState<Producto | null>(null);
  const [estado, setEstado] = useState('TODOS');
  if (!data) return null;
  const pedidos = data.pedidos.filter(p => estado === 'TODOS' || p.estado === estado).slice().reverse();
  const contar = (e: Pedido['estado']) => data.pedidos.filter(p => p.estado === e).length;
  const evaluar = () => void act(api.evaluar, list => `Evaluación completada · ${list.length} ${list.length === 1 ? 'pedido generado' : 'pedidos generados'}.`);
  return <>
    <div className="metrics-grid three">
      <Metric label="Pendientes de envío" value={contar('PENDIENTE')} hint="Solicitudes al centro de distribución" icon={Clock3} />
      <Metric label="En camino" value={contar('ENVIADO')} hint="Listos para recibir en depósito" icon={Truck} />
      <Metric label="Recepciones completas" value={contar('RECIBIDO')} hint="Mercadería incorporada al stock" icon={CheckCheck} />
    </div>

    <section className="panel">
      <div className="panel-heading">
        <div><h2>Pedidos de reposición</h2><p>Reposición desde el centro de distribución según la política de cada artículo.</p></div>
        <Button variant="primary" icon={RefreshCw} loading={busy} onClick={evaluar}>Evaluar reposición</Button>
      </div>
      <div className="table-toolbar">
        <span className="caption">Se descuentan los pedidos pendientes y enviados.</span>
        <select aria-label="Estado de los pedidos" value={estado} onChange={e => setEstado(e.target.value)}>
          <option value="TODOS">Todos los estados</option>
          {Object.entries(orderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="Pedidos de reposición">
        <table>
          <thead>
            <tr>
              <th scope="col">Pedido</th><th scope="col">Artículo</th><th scope="col" className="numeric">Unidades</th>
              <th scope="col">Fecha</th><th scope="col">Estado</th><th scope="col"><span className="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            {pedidos.map(p => (
              <tr key={p.id}>
                <td><strong>{sku(p.id)}</strong></td>
                <td>{data.productos.find(s => s.id === p.stockId)?.nombre || `Artículo ${p.stockId}`}</td>
                <td className="numeric">{number(p.cantidad)}</td>
                <td className="nowrap">{date(p.fecha)}</td>
                <td><Badge tone={orderTones[p.estado]}>{orderLabels[p.estado]}</Badge></td>
                <td><AccionPedido pedido={p} onRecibir={() => setReceiving(p)} onCancelar={() => setCanceling(p)} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!pedidos.length && <Empty title="Sin pedidos para mostrar" text="Evaluá la reposición o seleccioná otro estado." />}
    </section>

    <section className="panel">
      <div className="panel-heading">
        <div><h2>Políticas por artículo</h2><p>Definí cómo se anticipa la reposición y cuándo se envía un aviso.</p></div>
        <Settings2 size={24} aria-hidden="true" />
      </div>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="Políticas de reposición">
        <table>
          <thead>
            <tr>
              <th scope="col">Artículo</th><th scope="col">Política</th><th scope="col" className="numeric">Disponible</th>
              <th scope="col">Estado</th><th scope="col">Avisar a</th><th scope="col"><span className="sr-only">Configuración</span></th>
            </tr>
          </thead>
          <tbody>
            {data.productos.filter(p => p.tipo === 'SIMPLE').map(p => <FilaPolitica key={p.id} data={data} product={p} onConfigurar={() => setPolicy(p)} />)}
          </tbody>
        </table>
      </div>
    </section>

    {receiving && <ReceiveOrder order={receiving} onClose={() => setReceiving(null)} />}
    {policy && <PolicyForm product={policy} onClose={() => setPolicy(null)} />}
    {canceling && (
      <Confirmation
        title={`Cancelar pedido ${sku(canceling.id)}`}
        text="El pedido dejará de contar como mercadería pendiente de recibir. Esta acción no modifica el stock físico."
        danger
        label="Cancelar pedido"
        onClose={() => setCanceling(null)}
        onConfirm={() => run(() => api.cancelarPedido(canceling.id), 'Pedido cancelado.')}
      />
    )}
  </>;
}

const nombreDe = (data: Snapshot, stockId: number) => data.productos.find(p => p.id === stockId)?.nombre;

function TablaMovimientos({ data, movimientos }: { data: Snapshot; movimientos: Movimiento[] }) {
  return (
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
            <td>{nombreDe(data, m.stockId)}</td>
            <td><span className="nowrap">{movementLabels[m.tipo]}</span></td>
            <td className="numeric">{number(m.cantidad)}</td>
            <td><div className="stacked-cell"><span>{m.usuario}</span><span className="caption">{m.motivo}</span></div></td>
            <td className="nowrap">{date(m.fecha)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TablaAuditoria({ data, registros }: { data: Snapshot; registros: Auditoria[] }) {
  return (
    <table>
      <thead>
        <tr>
          <th scope="col">Artículo</th><th scope="col" className="numeric">Antes</th><th scope="col" className="numeric">Después</th>
          <th scope="col">Detalle</th><th scope="col">Fecha</th>
        </tr>
      </thead>
      <tbody>
        {registros.map((a, i) => (
          <tr key={`${a.stockId}:${a.fecha}:${i}`}>
            <td>{nombreDe(data, a.stockId)}</td>
            <td className="numeric">{a.disponibleAnterior === null ? '—' : number(a.disponibleAnterior)}</td>
            <td className="numeric available">{number(a.disponibleActual)}</td>
            <td>{a.detalle}</td>
            <td className="nowrap">{date(a.fecha)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

const PAGINA_ACTIVIDAD = 25;
function ActivityPanel() {
  const { data, revision } = useStore();
  const [stockId, setStockId] = useState('TODOS');
  const [mode, setMode] = useState<'movimientos' | 'auditoria'>('movimientos');
  const [pagina, setPagina] = useState(0);
  const filtro = { stockId: stockId === 'TODOS' ? undefined : Number(stockId), limite: PAGINA_ACTIVIDAD, pagina };
  const seleccion = `${stockId}:${pagina}`;
  const movimientos = useResource(`actividad:movimientos:${mode === 'movimientos' ? seleccion : 'inactiva'}`,
    signal => mode === 'movimientos' ? api.movimientos(filtro, signal) : Promise.resolve({ elementos: [] as Movimiento[], total: 0 }), revision);
  const auditoria = useResource(`actividad:auditoria:${mode === 'auditoria' ? seleccion : 'inactiva'}`,
    signal => mode === 'auditoria' ? api.auditoria(filtro, signal) : Promise.resolve({ elementos: [] as Auditoria[], total: 0 }), revision);
  if (!data) return null;
  const actual = mode === 'movimientos' ? movimientos : auditoria;
  const total = actual.data?.total ?? 0;
  const paginas = Math.max(1, Math.ceil(total / PAGINA_ACTIVIDAD));
  const cargandoPrimeraVez = actual.loading && !actual.data;
  const cambiar = (accion: () => void) => { accion(); setPagina(0); };
  return (
    <section className="panel">
      <div className="panel-heading">
        <div><h2>Actividad del inventario</h2><p>Movimientos y cambios de disponibilidad · horario de Argentina.</p></div>
        <Activity size={24} aria-hidden="true" />
      </div>
      <div className="table-toolbar">
        <div className="segmented" role="group" aria-label="Tipo de actividad">
          <button className={mode === 'movimientos' ? 'active' : ''} aria-pressed={mode === 'movimientos'} onClick={() => cambiar(() => setMode('movimientos'))}>Movimientos</button>
          <button className={mode === 'auditoria' ? 'active' : ''} aria-pressed={mode === 'auditoria'} onClick={() => cambiar(() => setMode('auditoria'))}>Auditoría</button>
        </div>
        <select aria-label="Artículo de actividad" value={stockId} onChange={e => { const value = e.target.value; cambiar(() => setStockId(value)); }}>
          <option value="TODOS">Todos los artículos</option>
          {data.productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
        </select>
      </div>
      <ErrorMessage error={actual.error} />
      <div className="table-scroll" tabIndex={0} role="region" aria-label={mode === 'movimientos' ? 'Movimientos del inventario' : 'Auditoría del stock'} aria-busy={actual.loading}>
        {mode === 'movimientos'
          ? <TablaMovimientos data={data} movimientos={movimientos.data?.elementos ?? []} />
          : <TablaAuditoria data={data} registros={auditoria.data?.elementos ?? []} />}
      </div>
      {cargandoPrimeraVez && <p className="muted loading-label">Cargando actividad…</p>}
      {!cargandoPrimeraVez && total === 0 && !actual.error && <Empty title="Sin actividad" text="Los cambios confirmados del artículo aparecerán acá." />}
      <footer className="table-footer">
        <span>{number(total)} registros · Página {pagina + 1} de {paginas}</span>
        <span className="pagination">
          <Button variant="ghost" icon={ChevronLeft} disabled={pagina === 0 || actual.loading} onClick={() => setPagina(p => p - 1)}>Anterior</Button>
          <Button variant="ghost" icon={ChevronRight} disabled={pagina + 1 >= paginas || actual.loading} onClick={() => setPagina(p => p + 1)}>Siguiente</Button>
        </span>
      </footer>
    </section>
  );
}

export function PurchasesPage({ tab }: { tab: string }) {
  const { data } = useStore();
  const [create, setCreate] = useState<'product' | 'combo' | null>(null);
  const [detail, setDetail] = useState<Producto | null>(null);
  const active = tabs.some(([id]) => id === tab) ? tab : 'reposicion';
  const enCurso = data?.pedidos.filter(p => p.estado === 'PENDIENTE' || p.estado === 'ENVIADO').length ?? 0;
  const accionesCatalogo = <>
    <Button icon={Plus} onClick={() => setCreate('combo')}>Nuevo combo</Button>
    <Button variant="primary" icon={Plus} onClick={() => setCreate('product')}>Nuevo artículo</Button>
  </>;
  return <>
    <PageHeading eyebrow="Backoffice" title="Compras y abastecimiento" text="Anticipá la reposición y administrá el catálogo de la sucursal." actions={active === 'catalogo' && accionesCatalogo} />
    <nav className="tabs" aria-label="Secciones de compras">
      {tabs.map(([id, label]) => (
        <a key={id} href={`#/compras/${id}`} className={active === id ? 'active' : ''} aria-current={active === id ? 'page' : undefined}>
          {label}
          {id === 'reposicion' && enCurso > 0 && <span className="tab-count">{enCurso}</span>}
        </a>
      ))}
    </nav>
    {active === 'reposicion' && <Replenishment />}
    {active === 'temporadas' && <SeasonsPanel />}
    {active === 'catalogo' && <InventoryTable onSelect={setDetail} />}
    {active === 'promociones' && <PanelPromociones />}
    {active === 'actividad' && <ActivityPanel />}
    {create && <ProductForm combo={create === 'combo'} onClose={() => setCreate(null)} />}
    {detail && <ProductDetail product={data?.productos.find(p => p.id === detail.id) || detail} onClose={() => setDetail(null)} />}
    {active === 'reposicion' && (
      <div className="page-footnote">
        <ClipboardList size={24} aria-hidden="true" />
        Los pedidos recibidos se incorporan al depósito en una única operación.
        <a href="#/recepcion" className="text-link">Ir a recepción <ArrowRight size={24} aria-hidden="true" /></a>
      </div>
    )}
  </>;
}
