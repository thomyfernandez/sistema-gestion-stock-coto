import { useState } from 'react';
import { ArrowRightLeft, Boxes, ClipboardList, Layers3, PackageCheck, Plus } from 'lucide-react';
import { useStore } from '../store';
import type { Producto } from '../types';
import { OperationForm } from '../components/OperationForm';
import { Button, Metric, Modal, PageHeading } from '../components/ui';
import { SucursalPage } from './Operations';
import { DigitalPage } from './Digital';

type OperacionStock = 'sucursal' | 'gestion-digital';
const textosOperacion: Record<OperacionStock, { titulo: string; descripcion: string }> = {
  sucursal: { titulo: 'Registrar movimiento interno', descripcion: 'Traslados entre depósito y góndola, devoluciones y mermas.' },
  'gestion-digital': { titulo: 'Registrar operación digital', descripcion: 'Reservas, cancelaciones y despachos del canal online.' },
};
const pestanas = [['inventario', 'Existencias'], ['digital', 'Reservas y despachos']] as const;

/** Métricas de Stock: suman sólo artículos simples, porque los combos no tienen contadores propios. */
function MetricasStock({ productos }: { productos: Producto[] }) {
  const simples = productos.filter(p => p.tipo === 'SIMPLE');
  const sumar = (valor: (p: Producto) => number) => simples.reduce((s, p) => s + valor(p), 0);
  return <div className="metrics-grid">
    <Metric label="Artículos" value={productos.length} hint={`${simples.length} simples · ${productos.length - simples.length} combos`} icon={Boxes} />
    <Metric label="Disponible en depósito" value={sumar(p => p.disponible)} hint="Unidades de artículos simples" icon={PackageCheck} />
    <Metric label="En góndola" value={sumar(p => p.stockExhibicion || 0)} hint="Unidades en exhibición" icon={Layers3} />
    <Metric label="Reservado online" value={sumar(p => p.stockReservado || 0)} hint="Comprometido en artículos simples" icon={ClipboardList} />
  </div>;
}

export function StockPage({ tab }: { tab: string }) {
  const { data, error, tienePermiso } = useStore();
  const [operation, setOperation] = useState<OperacionStock | null>(null);
  if (!data) return null;
  const haySimples = data.productos.some(p => p.tipo === 'SIMPLE');
  const puedeOperarDigital = tienePermiso('operarDigital');
  const activa = tab === 'digital' ? 'digital' : 'inventario';
  const acciones = <>
    <Button icon={ArrowRightLeft} disabled={!!error || !haySimples} onClick={() => setOperation('sucursal')}>Movimiento interno</Button>
    {puedeOperarDigital && <Button variant="primary" icon={Plus} disabled={!!error || !data.productos.length} onClick={() => setOperation('gestion-digital')}>Reserva o despacho</Button>}
  </>;
  return <>
    <PageHeading eyebrow="Control de existencias" title="Stock" text="Existencias, movimientos internos y reservas online en un solo lugar." actions={acciones} />
    <MetricasStock productos={data.productos} />
    <nav className="tabs" aria-label="Secciones de stock">
      {pestanas.map(([id, label]) => (
        <a key={id} href={`#/stock/${id}`} className={activa === id ? 'active' : ''} aria-current={activa === id ? 'page' : undefined}>{label}</a>
      ))}
    </nav>
    {activa === 'digital' ? <DigitalPage /> : <SucursalPage />}
    {operation && (
      <Modal title={textosOperacion[operation].titulo} description={textosOperacion[operation].descripcion} onClose={() => setOperation(null)}>
        <div className="modal-body"><OperationForm mode={operation} compact onComplete={() => setOperation(null)} onCancel={() => setOperation(null)} /></div>
      </Modal>
    )}
  </>;
}
