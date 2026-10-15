import { cloneElement, useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactElement, type ReactNode } from 'react';
import { AlertCircle, ArrowRight, Box, CheckCircle2, Layers3, LoaderCircle, Package, X, type LucideIcon } from 'lucide-react';
import { ApiError } from '../api';
import { money, number, sku, stockStatus } from '../format';
import { useStore } from '../store';
import type { Producto } from '../types';

export function Button({ children, icon: Icon, variant = 'secondary', loading, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { icon?: LucideIcon; variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; loading?: boolean }) {
  return <button type="button" className={`button ${variant} ${className}`} {...props} disabled={props.disabled || loading}>
    {loading ? <LoaderCircle size={24} className="spin" aria-hidden="true" /> : Icon && <Icon size={24} aria-hidden="true" />}{children}
  </button>;
}
export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactElement }) {
  const id = useId();
  return <div className="field"><label htmlFor={id}>{label}</label>{cloneElement(children as ReactElement<{ id: string; 'aria-describedby'?: string }>, { id, 'aria-describedby': hint ? `${id}-hint` : undefined })}
    {hint && <span className="field-hint" id={`${id}-hint`}>{hint}</span>}</div>;
}
export function ErrorMessage({ error }: { error?: Error | null }) {
  if (!error) return null;
  return <div className="message danger" role="alert"><AlertCircle size={24} aria-hidden="true" /><div><strong>{error.message}</strong>
    {error instanceof ApiError && Object.keys(error.errores).length > 0 && <ul>{Object.entries(error.errores).map(([key, values]) => <li key={key}>{key}: {values.join('. ')}</li>)}</ul>}</div></div>;
}
export function Modal({ title, description, children, onClose, wide = false }: { title: string; description?: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const { busy } = useStore();
  useEffect(() => {
    const dialog = ref.current;
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (trigger?.isConnected) trigger.focus();
    };
  }, []);
  return <dialog ref={ref} className={`modal ${wide ? 'wide' : ''}`} aria-labelledby={titleId}
    onCancel={e => { e.preventDefault(); if (!busy) onClose(); }} onClick={e => { if (e.target === e.currentTarget && !busy) onClose(); }}>
    <div className="modal-surface"><header className="modal-header"><div><h2 id={titleId}>{title}</h2>{description && <p>{description}</p>}</div>
      <Button variant="ghost" className="icon-button" icon={X} aria-label="Cerrar" onClick={onClose} disabled={busy} /></header>{children}</div>
  </dialog>;
}
export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'primary' }) {
  return <span className={`badge ${tone}`}><span className="badge-dot" />{children}</span>;
}
export function Empty({ title = 'No hay resultados', text = 'Probá con otra búsqueda o cambiá los filtros.', action }: { title?: string; text?: string; action?: ReactNode }) {
  return <div className="empty"><div className="empty-icon"><Package size={32} aria-hidden="true" /></div><h3>{title}</h3><p>{text}</p>{action}</div>;
}
export function PageHeading({ eyebrow, title, text, actions }: { eyebrow?: string; title: string; text: string; actions?: ReactNode }) {
  return <header className="page-heading"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1><p>{text}</p></div><div className="page-actions">{actions}</div></header>;
}
export function Metric({ label, value, hint, icon: Icon, tone = '' }: { label: string; value: string | number; hint: string; icon: LucideIcon; tone?: string }) {
  return <article className="metric"><div className="metric-top"><span>{label}</span><span className={`metric-icon ${tone}`}><Icon size={24} aria-hidden="true" /></span></div><strong className="metric-value">{typeof value === 'number' ? number(value) : value}</strong><span className="caption">{hint}</span></article>;
}
export function ProductName({ product }: { product: Pick<Producto, 'id' | 'nombre' | 'tipo'> }) {
  const Icon = product.tipo === 'COMBO' ? Layers3 : Package;
  return <div className="product-name"><span className={`product-mark ${product.tipo === 'COMBO' ? 'combo' : ''}`}><Icon size={24} aria-hidden="true" /></span>
    <div><strong>{product.nombre}</strong><span className="caption">{sku(product.id)} · {product.tipo === 'COMBO' ? 'Combo promocional' : 'Artículo simple'}</span></div></div>;
}
export function ProductDetail({ product, onClose, onMove }: { product: Producto; onClose: () => void; onMove?: () => void }) {
  const { data } = useStore();
  const status = stockStatus(product, data?.politicas[product.id]);
  return <Modal title="Detalle del artículo" description="Existencias confirmadas en el inventario." onClose={onClose}>
    <div className="modal-body"><ProductName product={product} /><p className="muted">{product.descripcion}</p><div className="split-line"><span>Precio de lista</span><strong>{money(product.precio)}</strong></div>
      <div className="stock-highlight"><span>Disponible para reservar</span><strong>{number(product.disponible)}</strong><Badge tone={status.tone}>{status.label}</Badge></div>
      {product.tipo === 'SIMPLE' ? <><div className="three-stats"><div><span>Físico</span><strong>{number(product.stockFisico!)}</strong></div><div><span>En góndola</span><strong>{number(product.stockExhibicion!)}</strong></div><div><span>Reservado</span><strong>{number(product.stockReservado!)}</strong></div></div>
        <p className="field-hint">El disponible descuenta las unidades en góndola y las reservas digitales del stock físico.</p></> : <><h3>Composición del combo</h3><div className="component-list">{product.componentes.map(c => <div key={c.stockId}><span><Box size={24} aria-hidden="true" />{c.nombre}</span><strong>× {c.cantidadEnCombo}</strong></div>)}</div><p className="field-hint">La disponibilidad depende de sus componentes. El combo no tiene existencias propias.</p></>}
    </div><footer className="modal-footer"><Button onClick={onClose}>Cerrar</Button>{onMove && <Button variant="primary" icon={ArrowRight} onClick={onMove}>Registrar movimiento</Button>}</footer>
  </Modal>;
}
export function Confirmation({ title, text, children, onClose, onConfirm, label, danger = false }: { title: string; text: string; children?: ReactNode; onClose: () => void; onConfirm: () => Promise<unknown>; label: string; danger?: boolean }) {
  const { busy } = useStore();
  const errorRef = useRef<HTMLDivElement>(null);
  return <Modal title={title} onClose={onClose}><div className="modal-body"><p>{text}</p>{children}<div ref={errorRef} role="alert" className="inline-error" /></div>
    <footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Volver</Button><Button variant={danger ? 'danger' : 'primary'} loading={busy} onClick={() => {
      if (errorRef.current) errorRef.current.textContent = '';
      void onConfirm().then(onClose).catch((e: Error) => { if (errorRef.current) errorRef.current.textContent = e.message; });
    }}>{label}</Button></footer></Modal>;
}
export function SuccessIcon() { return <CheckCircle2 size={24} aria-hidden="true" />; }
