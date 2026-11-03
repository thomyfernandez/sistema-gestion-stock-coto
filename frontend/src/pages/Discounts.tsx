import { useState } from 'react';
import { Edit3, Plus, Trash2 } from 'lucide-react';
import { api } from '../api';
import { money } from '../format';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Promocion } from '../types';
import { PromotionForm } from '../components/CatalogForms';
import { Badge, Button, Confirmation, Empty, ErrorMessage, Field, PageHeading } from '../components/ui';

function PriceSimulation({ productId, cantidad, medioPago }: { productId: number; cantidad: number; medioPago: string }) {
  const { revision } = useStore();
  const quote = useResource(
    `simulacion:${productId}:${cantidad}:${medioPago}:${revision}`,
    signal => api.precio(productId, cantidad, medioPago, signal)
  );
  return (
    <div className="quote-box" aria-live="polite">
      {quote.loading ? (
        <p>Calculando precio…</p>
      ) : quote.error ? (
        <ErrorMessage error={quote.error} />
      ) : (
        quote.data && (
          <>
            <div className="split-line">
              <span>Precio de lista</span>
              <span>{money(quote.data.precioLista)}</span>
            </div>
            <div className="split-line positive">
              <span>Ahorro total</span>
              <strong>-{money(quote.data.ahorro)}</strong>
            </div>
            <div className="split-line quote-total">
              <span>Total final</span>
              <strong>{money(quote.data.precioFinal)}</strong>
            </div>
            <span className="caption">
              {quote.data.promocionesAplicadas.length}{' '}
              {quote.data.promocionesAplicadas.length === 1 ? 'promoción aplicada' : 'promociones aplicadas'} · importe por {cantidad}{' '}
              {cantidad === 1 ? 'unidad' : 'unidades'}
            </span>
          </>
        )
      )}
    </div>
  );
}

export function DiscountsPage() {
  const { data, revision, busy, act } = useStore();
  const [productId, setProductId] = useState(data?.productos[0]?.id || 0);
  const [editing, setEditing] = useState<Promocion | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Promocion | null>(null);
  const [cantidad, setCantidad] = useState('3');
  const [medioPago, setMedioPago] = useState('DEBITO');

  if (!data || !data.productos.length) {
    return (
      <>
        <PageHeading
          eyebrow="Backoffice"
          title="Descuentos y promociones"
          text="Administrá los descuentos y promociones de cada artículo del catálogo."
        />
        <Empty
          title="El catálogo está vacío"
          text="Creá al menos un artículo antes de configurar promociones y descuentos."
        />
      </>
    );
  }

  const selectedProduct = data.productos.find(p => p.id === productId) || data.productos[0];
  const activeProductId = selectedProduct.id;

  const promos = useResource(
    `promociones:${activeProductId}:${revision}`,
    signal => api.promociones(activeProductId, signal)
  );

  return (
    <>
      <PageHeading
        eyebrow="Backoffice"
        title="Descuentos y promociones"
        text="Administrá los descuentos, 2×1 y beneficios por medio de pago de cada artículo."
        actions={
          <Button
            variant="primary"
            icon={Plus}
            disabled={promos.loading || !!promos.error || busy}
            onClick={() => setEditing('new')}
          >
            Nueva promoción
          </Button>
        }
      />

      <div className="promotion-layout">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2>Promociones acumulables</h2>
              <p>Los beneficios se aplican en el orden definido sobre el artículo seleccionado.</p>
            </div>
            <Button
              variant="primary"
              icon={Plus}
              disabled={promos.loading || !!promos.error || busy}
              onClick={() => setEditing('new')}
            >
              Nueva promoción
            </Button>
          </div>

          <div className="panel-body">
            <Field label="Artículo a gestionar">
              <select
                value={activeProductId}
                onChange={e => setProductId(Number(e.target.value))}
                aria-label="Seleccionar artículo"
              >
                {data.productos.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.nombre} ({p.tipo === 'COMBO' ? 'Combo' : 'Simple'})
                  </option>
                ))}
              </select>
            </Field>

            <ErrorMessage error={promos.error} />

            {promos.loading ? (
              <p className="muted loading-label">Cargando promociones…</p>
            ) : (
              <div className="promotion-list">
                {promos.data?.map(p => (
                  <article className={`promotion-row ${!p.activa ? 'inactive' : ''}`} key={p.id}>
                    <span className="promotion-order" title={`Orden de aplicación: ${p.orden}`}>
                      {p.orden}
                    </span>

                    <div className="promotion-copy">
                      <div className="split-line" style={{ justifyContent: 'flex-start', gap: 'var(--space-1)' }}>
                        <strong>
                          {p.tipo === 'DOS_POR_UNO'
                            ? '2×1 en unidades'
                            : p.tipo === 'MEDIO_PAGO'
                            ? `${p.porcentaje}% de descuento con ${p.medioPago}`
                            : `${p.porcentaje}% de descuento`}
                        </strong>
                        <Badge tone={p.activa ? 'success' : 'neutral'}>
                          {p.activa ? 'Activa' : 'Inactiva'}
                        </Badge>
                      </div>
                      <span className="caption">
                        {p.tipo === 'MEDIO_PAGO'
                          ? `Aplica pagando con ${p.medioPago}`
                          : p.tipo === 'DESCUENTO'
                          ? 'Sobre el importe acumulado según orden'
                          : 'Cada dos unidades, pagás una'}
                      </span>
                    </div>

                    <label className="switch" title={p.activa ? 'Desactivar promoción' : 'Activar promoción'}>
                      <input
                        type="checkbox"
                        role="switch"
                        checked={p.activa}
                        disabled={busy}
                        aria-label={`${p.activa ? 'Desactivar' : 'Activar'} promoción ${p.id}`}
                        onChange={() => {
                          const { id, productoId, ...body } = p;
                          void act(
                            () => api.guardarPromocion(productoId, { ...body, activa: !body.activa }, id),
                            'Estado de la promoción actualizado.'
                          );
                        }}
                      />
                      <span />
                    </label>

                    <Button
                      variant="ghost"
                      className="icon-button"
                      icon={Edit3}
                      aria-label={`Editar promoción ${p.id}`}
                      onClick={() => setEditing(p)}
                      disabled={busy}
                    />

                    <Button
                      variant="ghost"
                      className="icon-button"
                      icon={Trash2}
                      aria-label={`Eliminar promoción ${p.id}`}
                      onClick={() => setDeleting(p)}
                      disabled={busy}
                    />
                  </article>
                ))}

                {!promos.data?.length && !promos.error && (
                  <Empty
                    title="Sin promociones para este artículo"
                    text="Agregá un descuento porcentual, un 2×1 o un descuento por medio de pago."
                  />
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
              <input
                required
                type="number"
                min="1"
                max="2147483647"
                step="1"
                value={cantidad}
                onChange={e => setCantidad(e.target.value)}
              />
            </Field>

            <Field label="Medio de pago">
              <select value={medioPago} onChange={e => setMedioPago(e.target.value)}>
                <option value="DEBITO">Débito</option>
                <option value="CREDITO">Crédito</option>
                <option value="EFECTIVO">Efectivo</option>
              </select>
            </Field>

            {Number.isInteger(Number(cantidad)) && Number(cantidad) >= 1 && Number(cantidad) <= 2147483647 ? (
              <PriceSimulation
                productId={activeProductId}
                cantidad={Number(cantidad)}
                medioPago={medioPago}
              />
            ) : (
              <p className="muted">Ingresá una cantidad entera válida para simular.</p>
            )}

            <p className="field-hint">
              El precio de lista del artículo no se modifica. La simulación no genera reservas ni pedidos.
            </p>
          </div>
        </aside>
      </div>

      {editing && (
        <PromotionForm
          key={`${selectedProduct.id}:${editing === 'new' ? 'new' : editing.id}`}
          product={selectedProduct}
          existing={editing === 'new' ? undefined : editing}
          nextOrder={Math.max(0, ...(promos.data?.map(p => p.orden) || [])) + 10}
          onClose={() => setEditing(null)}
        />
      )}

      {deleting && (
        <Confirmation
          title={`Eliminar promoción #${deleting.id}`}
          text={`¿Estás seguro de que querés eliminar esta promoción (${
            deleting.tipo === 'DOS_POR_UNO' ? '2×1 en unidades' : `${deleting.porcentaje}% de descuento`
          }) del artículo ${selectedProduct.nombre}? Esta acción no se puede deshacer.`}
          danger
          label="Eliminar promoción"
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await act(
              () => api.eliminarPromocion(selectedProduct.id, deleting.id),
              'Promoción eliminada correctamente.'
            );
            setDeleting(null);
          }}
        />
      )}
    </>
  );
}

