import { useState, type Dispatch, type FormEvent, type SetStateAction } from 'react';
import { AlertCircle, CalendarRange, LoaderCircle, Mail, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { api } from '../api';
import { factor as formatoFactor, fechaDia, number, objetivoConFactor } from '../format';
import { MAX_DEMANDA_DIARIA, MAX_INT_32, MAX_OBJETIVO_ESTACIONAL, MAX_PRECIO, MAX_PUNTO_PEDIDO } from '../limites';
import { destinoAviso } from '../notifications';
import { MEDIO_PAGO_POR_DEFECTO, MEDIOS_PAGO } from '../promociones';
import { useStore } from '../store';
import { useResource } from '../useResource';
import type { Politica, Producto, Promocion } from '../types';
import { ImageUpload, type ImageSelection } from './ImageUpload';
import { Button, ErrorMessage, Field, Modal, OperadorSesion, ProductName } from './ui';

type LineaCombo = { stockId: number; cantidad: number };

/** Componentes del combo: cada artículo aparece una sola vez y siempre queda al menos una línea. */
function LineasCombo({ lineas, setLineas, productos, disabled }: {
  lineas: LineaCombo[]; setLineas: Dispatch<SetStateAction<LineaCombo[]>>; productos: Producto[]; disabled: boolean;
}) {
  const actualizar = (index: number, cambio: Partial<LineaCombo>) => setLineas(old => old.map((l, i) => i === index ? { ...l, ...cambio } : l));
  const agregar = () => {
    const next = productos.find(p => !lineas.some(l => l.stockId === p.id));
    if (next) setLineas(old => [...old, { stockId: next.id, cantidad: 1 }]);
  };
  return (
    <fieldset>
      <legend>Componentes del combo</legend>
      <p className="field-hint">Indicá cuántas unidades de cada componente incluye un combo.</p>
      <div className="combo-lines">
        {lineas.map((linea, index) => (
          <div className="combo-line" key={index}>
            <Field label={`Componente ${index + 1}`}>
              <select required value={linea.stockId || ''} disabled={disabled} onChange={e => actualizar(index, { stockId: Number(e.target.value) })}>
                {!linea.stockId && <option value="">Seleccioná un artículo</option>}
                {productos.filter(p => p.id === linea.stockId || !lineas.some(l => l.stockId === p.id)).map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </Field>
            <Field label={`Unidades ${index + 1}`}>
              <input required type="number" min="1" max={MAX_INT_32} step="1" value={linea.cantidad || ''} disabled={disabled} onChange={e => actualizar(index, { cantidad: Number(e.target.value) })} />
            </Field>
            <Button variant="ghost" icon={Trash2} className="icon-button" aria-label={`Quitar componente ${index + 1}`} disabled={disabled || lineas.length === 1} onClick={() => setLineas(old => old.filter((_, i) => i !== index))} />
          </div>
        ))}
      </div>
      <Button icon={Plus} disabled={disabled || lineas.length >= productos.length} onClick={agregar}>Agregar componente</Button>
    </fieldset>
  );
}

export function ProductForm({ combo, onClose }: { combo: boolean; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const [image, setImage] = useState<ImageSelection>({ loading: false, error: null });
  const [nombre, setNombre] = useState('');
  const [categoriaId, setCategoriaId] = useState<number | null>(null);
  const [descripcion, setDescripcion] = useState('');
  const [precio, setPrecio] = useState('');
  const [lineas, setLineas] = useState<LineaCombo[]>([{ stockId: data?.productos[0]?.id || 0, cantidad: 1 }]);
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { nombre: nombre.trim(), descripcion: descripcion.trim(), precio: Number(precio), categoriaId, ...(image.imagen ? { imagen: image.imagen } : {}) };
    try { await run(() => combo ? api.crearCombo({ ...body, componentes: lineas }) : api.crearProducto(body), combo ? 'Combo creado. Su disponibilidad se calcula a partir de los componentes.' : 'Artículo creado con existencias cero. Ya podés recibir mercadería.'); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return (
    <Modal title={combo ? 'Nuevo combo promocional' : 'Nuevo artículo'} description={combo ? 'Combiná artículos o combos existentes.' : 'Incorporá un artículo al catálogo de la sucursal.'} onClose={onClose} wide={combo}>
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <Field label="Nombre"><input required maxLength={160} value={nombre} onChange={e => setNombre(e.target.value)} disabled={busy} /></Field>
          <Field label="Descripción"><textarea required maxLength={500} rows={2} value={descripcion} onChange={e => setDescripcion(e.target.value)} disabled={busy} /></Field>
          <Field label="Precio de lista (ARS)"><input required type="number" step="0.01" min="0" max={MAX_PRECIO} value={precio} onChange={e => setPrecio(e.target.value)} disabled={busy} /></Field>
          <Field label="Categoría">
            <select value={categoriaId ?? ''} disabled={busy} onChange={e => setCategoriaId(e.target.value ? Number(e.target.value) : null)}>
              <option value="">Sin categoría</option>
              {data?.categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </Field>
          <a className="text-link" href="#/configuracion/categorias">Administrar categorías</a>
          <ImageUpload value={image} onChange={setImage} disabled={busy} />
          {combo && <LineasCombo lineas={lineas} setLineas={setLineas} productos={data?.productos ?? []} disabled={busy} />}
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button variant="primary" type="submit" loading={busy} disabled={image.loading || !!image.error || (combo && !data?.productos.length)}>{combo ? 'Crear combo' : 'Crear artículo'}</Button>
        </footer>
      </form>
    </Modal>
  );
}

export function PolicyForm({ product, onClose }: { product: Producto; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const initial = data?.politicas[product.id];
  const [inicial] = useState<Omit<Politica, 'stockId'>>(() => initial ? (({ stockId: _, ...rest }) => rest)(initial) : {
    tipo: 'PUNTO_PEDIDO', puntoPedido: 20, stockObjetivo: 100, demandaDiaria: 0, diasCobertura: 1, umbralAlerta: 10, destinoAlerta: 'configuracion-global', contactoId: null, canalAlerta: null,
  });
  const [values, setValues] = useState(inicial);
  const [error, setError] = useState<Error | null>(null);
  // Los destinatarios se administran en Configuración; la asignación anterior por contacto se conserva sin cambios.
  const aviso = data ? destinoAviso(data, product) : null;
  const setNumber = (name: keyof typeof values, value: string) => setValues(old => ({ ...old, [name]: value === '' ? '' : Number(value) }));
  const descriptions = {
    PUNTO_PEDIDO: 'Al alcanzar el punto de pedido, se repone hasta el stock objetivo.',
    DEMANDA: 'Se calcula la mercadería necesaria para cubrir la demanda diaria.',
    ESTACIONAL: 'El objetivo base se ajusta con el factor de la temporada que rige cada día.'
  };

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try {
      await run(() => api.guardarPolitica(product.id, values), 'Política de reposición guardada. Se aplicará en el próximo movimiento o evaluación.', ['politicas']);
      onClose();
    } catch (e) { setError(e as Error); }
  }

  return (
    <Modal title="Política de reposición" description="Configurá cuándo y cuánto reponer." onClose={onClose}>
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <ProductName product={product} />
          <Field label="Política">
            <select value={values.tipo} disabled={busy} onChange={e => setValues(old => ({ ...old, tipo: e.target.value as Politica['tipo'], puntoPedido: e.target.value === 'PUNTO_PEDIDO' ? Math.max(1, Number(old.puntoPedido)) : 0 }))}>
              <option value="PUNTO_PEDIDO">Punto de pedido</option>
              <option value="DEMANDA">Por demanda</option>
              <option value="ESTACIONAL">Estacional</option>
            </select>
          </Field>
          <p className="form-info">{descriptions[values.tipo]}</p>
          {values.tipo === 'PUNTO_PEDIDO' && (
            <div className="form-grid">
              <Field label="Punto de pedido"><input required type="number" min="1" max={MAX_PUNTO_PEDIDO} step="1" value={values.puntoPedido} onChange={e => setNumber('puntoPedido', e.target.value)} disabled={busy} /></Field>
              <Field label="Stock objetivo"><input required type="number" min={Number(values.puntoPedido) + 1} max={MAX_INT_32} step="1" value={values.stockObjetivo} onChange={e => setNumber('stockObjetivo', e.target.value)} disabled={busy} /></Field>
            </div>
          )}
          {values.tipo === 'DEMANDA' && (
            <div className="form-grid">
              <Field label="Demanda diaria"><input required type="number" min="0" max={MAX_DEMANDA_DIARIA} step="0.001" value={values.demandaDiaria} onChange={e => setNumber('demandaDiaria', e.target.value)} disabled={busy} /></Field>
              <Field label="Días de cobertura"><input required type="number" min="1" max={MAX_INT_32} step="1" value={values.diasCobertura} onChange={e => setNumber('diasCobertura', e.target.value)} disabled={busy} /></Field>
            </div>
          )}
          {values.tipo === 'ESTACIONAL' && <>
            {/* Tope: el objetivo base por el factor máximo de una temporada (10) tiene que entrar en un entero. */}
            <Field label="Objetivo base" hint="Unidades a reponer fuera de temporada."><input required type="number" min="1" max={MAX_OBJETIVO_ESTACIONAL} step="1" value={values.stockObjetivo} onChange={e => setNumber('stockObjetivo', e.target.value)} disabled={busy} /></Field>
            <SeasonFactorCard product={product} base={Number(values.stockObjetivo)} dirty={JSON.stringify(values) !== JSON.stringify(inicial)} onLeave={onClose} />
          </>}
          <div className="form-divider" />
          <Field label="Umbral de alerta" hint="Se envía un aviso cuando el disponible cae por debajo de este valor.">
            <input required type="number" min="1" max={MAX_INT_32} step="1" value={values.umbralAlerta} onChange={e => setNumber('umbralAlerta', e.target.value)} disabled={busy} />
          </Field>
          {aviso && (
            <div className="notice-resolution-card" role="region" aria-label="Destinatarios del aviso automático">
              <Mail size={24} className="notice-resolution-icon" aria-hidden="true" />
              <div className="notice-resolution-content">
                <p className="form-info">
                  Avisar a {aviso.nombre} · {aviso.canal}: {aviso.destinos.join(', ') || 'sin destino'}
                </p>
                <p className="field-hint">
                  <a className="text-link" href="#/configuracion/avisos" onClick={onClose}>
                    Administrar destinatarios de avisos
                  </a>
                </p>
              </div>
            </div>
          )}
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button type="submit" variant="primary" loading={busy}>Guardar política</Button>
        </footer>
      </form>
    </Modal>
  );
}

/** Factor que hoy aplica la política estacional, según el backend: el navegador no elige la temporada. */
function SeasonFactorCard({ product, base, dirty, onLeave }: { product: Producto; base: number; dirty: boolean; onLeave: () => void }) {
  const { data, revision } = useStore();
  const [intento, setIntento] = useState(0);
  const consulta = useResource(`factor:${product.id}`, signal => api.factorEstacional(product.id, signal), revision * 1000 + intento);
  const enlace = <>
    {dirty && <span className="caption">Guardá la política antes de salir: los cambios sin guardar se pierden.</span>}
    <a className="text-link" href="#/compras/temporadas" onClick={onLeave}>Administrar temporadas</a>
  </>;
  if (consulta.error && !consulta.data) return (
    <div className="notice-resolution-card warning" role="region" aria-label="Factor de temporada vigente" aria-live="polite">
      <AlertCircle size={24} aria-hidden="true" />
      <div className="notice-resolution-content">
        <p>No pudimos consultar la temporada de hoy.</p>
        <span className="caption">Podés guardar la política igual: el factor se calcula en cada evaluación de reposición.</span>
        <div className="notice-resolution-actions"><Button size="sm" icon={RefreshCw} loading={consulta.loading} onClick={() => setIntento(i => i + 1)}>Reintentar</Button>{enlace}</div>
      </div>
    </div>
  );
  const vigente = consulta.data;
  if (!vigente) return (
    <div className="notice-resolution-card" role="region" aria-label="Factor de temporada vigente" aria-live="polite" aria-busy="true">
      <LoaderCircle size={24} className="spin" aria-hidden="true" />
      <div className="notice-resolution-content"><p>Consultando la temporada de hoy…</p></div>
    </div>
  );
  const categoria = data?.categorias.find(c => c.id === product.categoriaId)?.nombre;
  const fecha = fechaDia(vigente.fecha);
  const conTemporada = vigente.origen !== 'SIN_TEMPORADA';
  const baseValida = Number.isInteger(base) && base > 0;
  return (
    <div className="notice-resolution-card" role="region" aria-label="Factor de temporada vigente" aria-live="polite" aria-busy={consulta.loading}>
      <CalendarRange size={24} aria-hidden="true" />
      <div className="notice-resolution-content">
        <p>{conTemporada ? `Hoy rige «${vigente.temporada}» · ${formatoFactor(vigente.factor)}` : 'Hoy no rige ninguna temporada · ×1'}</p>
        <span className="caption">{vigente.origen === 'ARTICULO' ? 'Temporada del artículo.' : vigente.origen === 'CATEGORIA' ? (categoria ? `Temporada de la categoría ${categoria}.` : 'Temporada de la categoría.') : 'Se repone el objetivo base.'}</span>
        <div className="notice-resolution-breakdown"><span>Objetivo de hoy</span>{baseValida ? <strong>{number(objetivoConFactor(base, vigente.factor))} unidades</strong> : <span className="caption">Ingresá el objetivo base para ver el objetivo de hoy.</span>}</div>
        <span className="caption">{conTemporada && baseValida ? `${number(base)} × ${number(vigente.factor)}, redondeado hacia arriba · ` : ''}al {fecha}</span>
        <div className="notice-resolution-actions">{enlace}</div>
      </div>
    </div>
  );
}

const TIPOS_PROMOCION: Record<Promocion['tipo'], string> = { DESCUENTO: 'Descuento porcentual', DOS_POR_UNO: '2×1', MEDIO_PAGO: 'Descuento por medio de pago' };

export function PromotionForm({ product, existing, nextOrder, onClose }: { product: Producto; existing?: Promocion; nextOrder: number; onClose: () => void }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState({ tipo: existing?.tipo || 'DESCUENTO' as Promocion['tipo'], porcentaje: String(existing?.porcentaje ?? 10), orden: String(existing?.orden ?? nextOrder), activa: existing?.activa ?? true, medioPago: existing?.medioPago || MEDIO_PAGO_POR_DEFECTO });
  const [error, setError] = useState<Error | null>(null);
  // Una promoción guardada con un medio que no está en la lista lo conserva como opción.
  const medioHeredado = existing?.medioPago && !MEDIOS_PAGO.some(m => m.valor === existing.medioPago) ? existing.medioPago : null;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { tipo: values.tipo, porcentaje: Number(values.porcentaje), orden: Number(values.orden), activa: values.activa, medioPago: values.tipo === 'MEDIO_PAGO' ? values.medioPago : null };
    try { await run(() => api.guardarPromocion(product.id, body, existing?.id), 'Promoción guardada. Los precios del catálogo se actualizaron.', []); onClose(); }
    catch (e) { setError(e as Error); }
  }
  const cambiarTipo = (tipo: Promocion['tipo']) => setValues(old => ({ ...old, tipo, porcentaje: tipo === 'DOS_POR_UNO' ? '0' : old.porcentaje }));
  return (
    <Modal title={existing ? 'Editar promoción' : 'Nueva promoción'} description="Los descuentos se acumulan según su orden de aplicación." onClose={onClose}>
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <ProductName product={product} />
          <Field label="Tipo de promoción">
            <select disabled={busy} value={values.tipo} onChange={e => cambiarTipo(e.target.value as Promocion['tipo'])}>
              {Object.entries(TIPOS_PROMOCION).map(([valor, etiqueta]) => <option key={valor} value={valor}>{etiqueta}</option>)}
            </select>
          </Field>
          {values.tipo === 'DOS_POR_UNO' ? (
            <p className="form-info">Cada dos unidades, se paga una. Las unidades impares se cobran a su precio correspondiente.</p>
          ) : (
            <Field label="Descuento (%)"><input required type="number" min="0" max="100" step="0.0001" value={values.porcentaje} onChange={e => setValues(old => ({ ...old, porcentaje: e.target.value }))} disabled={busy} /></Field>
          )}
          {values.tipo === 'MEDIO_PAGO' && (
            <Field label="Medio de pago">
              <select required value={values.medioPago} onChange={e => setValues(old => ({ ...old, medioPago: e.target.value }))} disabled={busy}>
                {MEDIOS_PAGO.map(m => <option key={m.valor} value={m.valor}>{m.etiqueta}</option>)}
                {medioHeredado && <option value={medioHeredado}>{medioHeredado}</option>}
              </select>
            </Field>
          )}
          <Field label="Orden de aplicación" hint="Debe ser distinto al de las otras promociones del artículo.">
            <input required type="number" min="0" max={MAX_INT_32} step="1" value={values.orden} onChange={e => setValues(old => ({ ...old, orden: e.target.value }))} disabled={busy} />
          </Field>
          <label className="checkbox-label"><input type="checkbox" checked={values.activa} onChange={e => setValues(old => ({ ...old, activa: e.target.checked }))} disabled={busy} />Promoción activa</label>
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button type="submit" variant="primary" loading={busy}>Guardar promoción</Button>
        </footer>
      </form>
    </Modal>
  );
}

export function ComboArmadoForm({ combo, mode, onClose }: { combo: Producto; mode: 'armar' | 'desarmar'; onClose: () => void }) {
  const { busy, run } = useStore();
  const [cantidad, setCantidad] = useState('1');
  const [motivo, setMotivo] = useState(mode === 'armar' ? 'Armado de combos para salón' : 'Desarmado por rotura o devolución');
  const [error, setError] = useState<Error | null>(null);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    try {
      await run(
        () => mode === 'armar'
          ? api.armarCombo(combo.id, { cantidad: Number(cantidad), motivo: motivo.trim() })
          : api.desarmarCombo(combo.id, { cantidad: Number(cantidad), motivo: motivo.trim() }),
        mode === 'armar' ? 'Combo armado correctamente. Stock propio actualizado.' : 'Combo desarmado correctamente. Componentes restituidos.'
      );
      onClose();
    } catch (e) {
      setError(e as Error);
    }
  }

  const limit = mode === 'desarmar' ? (combo.stockFisico || 0) : undefined;

  return (
    <Modal
      title={mode === 'armar' ? `Armar combo ${combo.nombre}` : `Desarmar combo ${combo.nombre}`}
      description={mode === 'armar'
        ? 'Pasa unidades físicas de los componentes al stock propio del combo.'
        : 'Descuenta stock físico propio del combo y restituye unidades a los componentes.'}
      onClose={onClose}
    >
      <form onSubmit={submit}>
        <div className="modal-body form-stack">
          <ProductName product={combo} />
          <Field
            label="Cantidad"
            hint={mode === 'desarmar' ? `Stock físico propio actual: ${combo.stockFisico || 0} unidades` : 'Se descuentan de los componentes según la fórmula del combo'}
          >
            <input
              required
              type="number"
              min="1"
              max={limit}
              step="1"
              value={cantidad}
              onChange={e => setCantidad(e.target.value)}
              disabled={busy}
            />
          </Field>
          <Field label="Motivo">
            <input
              required
              maxLength={500}
              value={motivo}
              onChange={e => setMotivo(e.target.value)}
              disabled={busy}
            />
          </Field>
          <OperadorSesion />
          <ErrorMessage error={error} />
        </div>
        <footer className="modal-footer">
          <Button onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button variant="primary" type="submit" loading={busy} disabled={mode === 'desarmar' && (combo.stockFisico || 0) <= 0}>
            {mode === 'armar' ? 'Confirmar armado' : 'Confirmar desarmado'}
          </Button>
        </footer>
      </form>
    </Modal>
  );
}
