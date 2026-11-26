import { useState } from 'react';
import { Edit3, Plus, Trash2 } from 'lucide-react';
import { api } from '../api';
import { money } from '../format';
import { MAX_INT_32 } from '../limites';
import { describirPromocion, MEDIO_PAGO_POR_DEFECTO, MEDIOS_PAGO } from '../promociones';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Precio, Promocion } from '../types';
import { PromotionForm } from './CatalogForms';
import { Badge, Button, Confirmation, Empty, ErrorMessage, Field } from './ui';

/** Las promociones no modifican existencias: sus cambios no recargan ningún grupo del inventario. */
const SIN_RECARGA = [] as const;

const cantidadValida = (valor: string) => {
  const cantidad = Number(valor);
  return Number.isInteger(cantidad) && cantidad >= 1 && cantidad <= MAX_INT_32;
};
const plural = (n: number, singular: string, varios: string) => (n === 1 ? singular : varios);

function DetallePrecio({ precio, cantidad }: { precio: Precio; cantidad: number }) {
  const aplicadas = precio.promocionesAplicadas.length;
  return <>
    <div className="split-line"><span>Precio de lista</span><span>{money(precio.precioLista)}</span></div>
    <div className="split-line positive"><span>Ahorro total</span><strong>{money(-precio.ahorro)}</strong></div>
    <div className="split-line quote-total"><span>Total final</span><strong>{money(precio.precioFinal)}</strong></div>
    <span className="caption">
      {aplicadas} {plural(aplicadas, 'promoción aplicada', 'promociones aplicadas')} · importe por {cantidad} {plural(cantidad, 'unidad', 'unidades')}
    </span>
  </>;
}

function SimulacionPrecio({ productId, cantidad, medioPago }: { productId: number; cantidad: number; medioPago: string }) {
  const { revision } = useStore();
  const quote = useResource(
    `simulacion:${productId}:${cantidad}:${medioPago}:${revision}`,
    signal => api.precio(productId, cantidad, medioPago, signal)
  );
  return (
    <div className="quote-box" aria-live="polite">
      {quote.loading && <p>Calculando precio…</p>}
      {!quote.loading && <ErrorMessage error={quote.error} />}
      {!quote.loading && !quote.error && quote.data && <DetallePrecio precio={quote.data} cantidad={cantidad} />}
    </div>
  );
}

function FilaPromocion({ promocion: p, onEditar, onEliminar }: { promocion: Promocion; onEditar: () => void; onEliminar: () => void }) {
  const { busy, act } = useStore();
  const { titulo, detalle } = describirPromocion(p);
  const alternar = () => {
    const { id, productoId, ...body } = p;
    void act(() => api.guardarPromocion(productoId, { ...body, activa: !body.activa }, id), 'Estado de la promoción actualizado.', SIN_RECARGA);
  };
  return (
    <article className={`promotion-row ${p.activa ? '' : 'inactive'}`}>
      <span className="promotion-order" title={`Orden de aplicación: ${p.orden}`}>{p.orden}</span>
      <div className="promotion-copy">
        <div className="promotion-title">
          <strong>{titulo}</strong>
          <Badge tone={p.activa ? 'success' : 'neutral'}>{p.activa ? 'Activa' : 'Inactiva'}</Badge>
        </div>
        <span className="caption">{detalle}</span>
      </div>
      <label className="switch" title={p.activa ? 'Desactivar promoción' : 'Activar promoción'}>
        <input type="checkbox" role="switch" checked={p.activa} disabled={busy} aria-label={`${p.activa ? 'Desactivar' : 'Activar'} promoción ${p.id}`} onChange={alternar} />
        <span />
      </label>
      <Button variant="ghost" className="icon-button" icon={Edit3} aria-label={`Editar promoción ${p.id}`} onClick={onEditar} disabled={busy} />
      <Button variant="ghost" className="icon-button" icon={Trash2} aria-label={`Eliminar promoción ${p.id}`} onClick={onEliminar} disabled={busy} />
    </article>
  );
}

/** Promociones acumulables y simulador de precio; se monta en Descuentos y en la pestaña Promociones de Compras. */
export function PanelPromociones() {
  const { data, revision, busy, run } = useStore();
  const [productId, setProductId] = useState(data?.productos[0]?.id || 0);
  const [editing, setEditing] = useState<Promocion | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Promocion | null>(null);
  const [cantidad, setCantidad] = useState('3');
  const [medioPago, setMedioPago] = useState<string>(MEDIO_PAGO_POR_DEFECTO);
  const productos = data?.productos ?? [];
  const producto = productos.find(p => p.id === productId) ?? productos[0];
  // El hook se llama siempre; sin artículos no hay nada que pedir.
  const promos = useResource(
    `promociones:${producto?.id ?? 'sin-catalogo'}:${revision}`,
    signal => (producto ? api.promociones(producto.id, signal) : Promise.resolve([] as Promocion[]))
  );
  if (!producto) return <Empty title="El catálogo está vacío" text="Creá al menos un artículo antes de configurar promociones y descuentos." />;

  return (
    <>
      <div className="promotion-layout">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Promociones acumulables</h2>
              <p>Los beneficios se aplican en el orden definido sobre el artículo seleccionado.</p>
            </div>
            <Button variant="primary" icon={Plus} disabled={promos.loading || !!promos.error || busy} onClick={() => setEditing('new')}>Nueva promoción</Button>
          </div>
          <div className="panel-body">
            <Field label="Artículo a gestionar">
              <select value={producto.id} onChange={e => setProductId(Number(e.target.value))}>
                {productos.map(p => <option key={p.id} value={p.id}>{p.nombre} ({p.tipo === 'COMBO' ? 'Combo' : 'Simple'})</option>)}
              </select>
            </Field>
            <ErrorMessage error={promos.error} />
            {promos.loading ? (
              <p className="muted loading-label">Cargando promociones…</p>
            ) : (
              <div className="promotion-list">
                {promos.data?.map(p => <FilaPromocion key={p.id} promocion={p} onEditar={() => setEditing(p)} onEliminar={() => setDeleting(p)} />)}
                {!promos.data?.length && !promos.error && (
                  <Empty title="Sin promociones para este artículo" text="Agregá un descuento porcentual, un 2×1 o un descuento por medio de pago." />
                )}
              </div>
            )}
          </div>
        </section>

        <aside className="panel simulator">
          <div className="panel-heading">
            <div>
              <h2>Simular precio final</h2>
              <p>Consultá cómo se aplican los beneficios según cantidad y medio de pago.</p>
            </div>
          </div>
          <div className="panel-body form-stack">
            <Field label="Unidades a valorizar">
              <input required type="number" min="1" max={MAX_INT_32} step="1" value={cantidad} onChange={e => setCantidad(e.target.value)} />
            </Field>
            <Field label="Medio de pago">
              <select value={medioPago} onChange={e => setMedioPago(e.target.value)}>
                {MEDIOS_PAGO.map(m => <option key={m.valor} value={m.valor}>{m.etiqueta}</option>)}
              </select>
            </Field>
            {cantidadValida(cantidad)
              ? <SimulacionPrecio productId={producto.id} cantidad={Number(cantidad)} medioPago={medioPago} />
              : <p className="muted">Ingresá una cantidad entera válida para simular.</p>}
            <p className="field-hint">El precio de lista del artículo no se modifica. La simulación no genera reservas ni pedidos.</p>
          </div>
        </aside>
      </div>

      {editing && (
        <PromotionForm
          key={`${producto.id}:${editing === 'new' ? 'new' : editing.id}`}
          product={producto}
          existing={editing === 'new' ? undefined : editing}
          nextOrder={Math.max(0, ...(promos.data?.map(p => p.orden) || [])) + 10}
          onClose={() => setEditing(null)}
        />
      )}

      {deleting && (
        <Confirmation
          title={`Eliminar promoción #${deleting.id}`}
          text={`¿Estás seguro de que querés eliminar esta promoción (${describirPromocion(deleting).titulo}) del artículo ${producto.nombre}? Esta acción no se puede deshacer.`}
          danger
          label="Eliminar promoción"
          onClose={() => setDeleting(null)}
          onConfirm={() => run(() => api.eliminarPromocion(producto.id, deleting.id), 'Promoción eliminada correctamente.', SIN_RECARGA)}
        />
      )}
    </>
  );
}
