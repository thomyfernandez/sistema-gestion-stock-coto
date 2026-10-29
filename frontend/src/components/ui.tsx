import { cloneElement, useState, useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactElement, type ReactNode, type FormEvent } from 'react';
import { AlertCircle, ArrowRight, Box, CheckCircle2, Layers3, LoaderCircle, Package, X, type LucideIcon } from 'lucide-react';
import { api, imagenSrc, ApiError } from '../api';
import { money, number, sku, stockStatus } from '../format';
import { useStore } from '../store';
import type { Producto } from '../types';

export type ButtonVariant = 'primary' | 'lineal' | 'secondary' | 'dark' | 'ghost' | 'success' | 'warning' | 'danger' | 'info';
export function Button({ children, icon: Icon, variant = 'secondary', size = 'md', iconPosition = 'left', loading, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { icon?: LucideIcon; variant?: ButtonVariant; size?: 'sm' | 'md' | 'lg'; iconPosition?: 'left' | 'right'; loading?: boolean }) {
  const glyph = loading ? <LoaderCircle size={24} className="spin" aria-hidden="true" /> : Icon && <Icon size={24} aria-hidden="true" />;
  return <button type="button" className={`button ${variant} ${size === 'md' ? '' : size} ${className}`} {...props} disabled={props.disabled || loading}>
    {iconPosition === 'left' && glyph}{children}{iconPosition === 'right' && glyph}
  </button>;
}
export function IconButton({ icon: Icon, label, appearance = 'naked', size = 'md', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { icon: LucideIcon; label: string; appearance?: 'naked' | 'lineal' | 'fill'; size?: 'sm' | 'md' | 'lg' }) {
  return <button type="button" className={`icon-btn ${appearance} ${size === 'md' ? '' : size} ${className}`} aria-label={label} title={label} {...props}><Icon aria-hidden="true" /></button>;
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
export function ProductName({ product }: { product: Pick<Producto, 'id' | 'nombre' | 'tipo' | 'imagenUrl'> }) {
  const Icon = product.tipo === 'COMBO' ? Layers3 : Package;
  return <div className="product-name"><span className={`product-mark ${product.tipo === 'COMBO' ? 'combo' : ''}`}>{product.imagenUrl ? <img className="product-thumbnail" src={imagenSrc(product.imagenUrl)} alt="" loading="lazy" /> : <Icon size={24} aria-hidden="true" />}</span>
    <div><strong>{product.nombre}</strong><span className="caption">{sku(product.id)} · {product.tipo === 'COMBO' ? 'Combo promocional' : 'Artículo simple'}</span></div></div>;
}
function ProductCategory({ product }: { product: Producto }) {
  const { data, busy, run } = useStore();
  const [categoryId, setCategoryId] = useState(product.categoriaId);
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.asignarCategoria(product.id, categoryId), 'Categoría del artículo actualizada.'); }
    catch (e) { setError(e as Error); }
  }
  return <form className="product-category-form" onSubmit={submit}><Field label="Categoría del artículo"><select value={categoryId ?? ''} disabled={busy} onChange={e => setCategoryId(e.target.value ? Number(e.target.value) : null)}><option value="">Sin categoría</option>{data?.categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}</select></Field><Button type="submit" loading={busy} disabled={categoryId === product.categoriaId}>Guardar categoría</Button><ErrorMessage error={error} /></form>;
}
export function ProductDetail({ product, onClose, onMove }: { product: Producto; onClose: () => void; onMove?: () => void }) {
  const { data } = useStore();
  const status = stockStatus(product, data?.politicas[product.id]);
  return <Modal title="Detalle del artículo" description="Existencias confirmadas en el inventario." onClose={onClose}>
    <div className="modal-body"><ProductName product={product} />{product.imagenUrl && <img className="product-detail-image" src={imagenSrc(product.imagenUrl)} alt={product.nombre} />}<p className="muted">{product.descripcion}</p><ProductCategory key={`${product.id}:${product.categoriaId}`} product={product} /><div className="split-line"><span>Precio de lista</span><strong>{money(product.precio)}</strong></div>
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
