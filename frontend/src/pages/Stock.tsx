import { useState } from 'react';
import { ArrowRightLeft, Boxes, ClipboardList, Layers3, PackageCheck, Plus } from 'lucide-react';
import { useStore } from '../store';
import { OperationForm } from '../components/OperationForm';
import { Button, Metric, Modal, PageHeading } from '../components/ui';
import { SucursalPage } from './Operations';
import { DigitalPage } from './Digital';

export function StockPage({ tab }: { tab: string }) {
  const { data, error } = useStore();
  const [operation, setOperation] = useState<'sucursal' | 'gestion-digital' | null>(null);
  if (!data) return null;
  const simples = data.productos.filter(p => p.tipo === 'SIMPLE');
  return <><PageHeading eyebrow="Control de existencias" title="Stock" text="Existencias, movimientos internos y reservas online en un solo lugar." actions={<><Button icon={ArrowRightLeft} disabled={!!error || !simples.length} onClick={() => setOperation('sucursal')}>Movimiento interno</Button><Button variant="primary" icon={Plus} disabled={!!error || !data.productos.length} onClick={() => setOperation('gestion-digital')}>Reserva o despacho</Button></>} />
    <div className="metrics-grid"><Metric label="Artículos" value={data.productos.length} hint={`${simples.length} simples · ${data.productos.length - simples.length} combos`} icon={Boxes} /><Metric label="Disponible en depósito" value={simples.reduce((s, p) => s + p.disponible, 0)} hint="Unidades de artículos simples" icon={PackageCheck} /><Metric label="En góndola" value={simples.reduce((s, p) => s + (p.stockExhibicion || 0), 0)} hint="Unidades en exhibición" icon={Layers3} /><Metric label="Reservado online" value={simples.reduce((s, p) => s + (p.stockReservado || 0), 0)} hint="Comprometido en artículos simples" icon={ClipboardList} /></div>
    <nav className="tabs" aria-label="Secciones de stock"><a href="#/stock/inventario" className={tab !== 'digital' ? 'active' : ''} aria-current={tab !== 'digital' ? 'page' : undefined}>Existencias</a><a href="#/stock/digital" className={tab === 'digital' ? 'active' : ''} aria-current={tab === 'digital' ? 'page' : undefined}>Reservas y despachos</a></nav>
    {tab === 'digital' ? <DigitalPage embedded /> : <SucursalPage embedded />}
    {operation && <Modal title={operation === 'sucursal' ? 'Registrar movimiento interno' : 'Registrar operación digital'} description={operation === 'sucursal' ? 'Traslados entre depósito y góndola, devoluciones y mermas.' : 'Reservas, cancelaciones y despachos del canal online.'} onClose={() => setOperation(null)}><div className="modal-body"><OperationForm mode={operation} compact onComplete={() => setOperation(null)} onCancel={() => setOperation(null)} /></div></Modal>}
  </>;
}
