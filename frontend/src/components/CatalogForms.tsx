import { useState, type FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { api } from '../api';
import { useStore } from '../store';
import type { Politica, Producto, Promocion } from '../types';
import { Button, ErrorMessage, Field, Modal, ProductName } from './ui';

export function ProductForm({ combo, onClose }: { combo: boolean; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const [nombre, setNombre] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [precio, setPrecio] = useState('');
  const [lineas, setLineas] = useState([{ stockId: data?.productos[0]?.id || 0, cantidad: 1 }]);
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { nombre: nombre.trim(), descripcion: descripcion.trim(), precio: Number(precio) };
    try { await run(() => combo ? api.crearCombo({ ...body, componentes: lineas }) : api.crearProducto(body), combo ? 'Combo creado. Su disponibilidad se calcula a partir de los componentes.' : 'Artículo creado con existencias cero. Ya podés recibir mercadería.'); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return <Modal title={combo ? 'Nuevo combo promocional' : 'Nuevo artículo'} description={combo ? 'Combiná artículos o combos existentes.' : 'Incorporá un artículo al catálogo de la sucursal.'} onClose={onClose} wide={combo}>
    <form onSubmit={submit}><div className="modal-body form-stack"><Field label="Nombre"><input required maxLength={160} value={nombre} onChange={e => setNombre(e.target.value)} disabled={busy} /></Field><Field label="Descripción"><textarea required maxLength={500} rows={2} value={descripcion} onChange={e => setDescripcion(e.target.value)} disabled={busy} /></Field><Field label="Precio de lista (ARS)"><input required type="number" step="0.01" min="0" max="9999999999999.99" value={precio} onChange={e => setPrecio(e.target.value)} disabled={busy} /></Field>
      {combo && <fieldset><legend>Componentes del combo</legend><p className="field-hint">Indicá cuántas unidades de cada componente incluye un combo.</p><div className="combo-lines">{lineas.map((linea, index) => <div className="combo-line" key={index}>
        <Field label={`Componente ${index + 1}`}><select required value={linea.stockId || ''} disabled={busy} onChange={e => setLineas(old => old.map((l, i) => i === index ? { ...l, stockId: Number(e.target.value) } : l))}>
          {!linea.stockId && <option value="">Seleccioná un artículo</option>}{data?.productos.filter(p => p.id === linea.stockId || !lineas.some(l => l.stockId === p.id)).map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}</select></Field>
        <Field label={`Unidades ${index + 1}`}><input required type="number" min="1" max="2147483647" step="1" value={linea.cantidad || ''} disabled={busy} onChange={e => setLineas(old => old.map((l, i) => i === index ? { ...l, cantidad: Number(e.target.value) } : l))} /></Field>
        <Button variant="ghost" icon={Trash2} className="icon-button" aria-label={`Quitar componente ${index + 1}`} disabled={busy || lineas.length === 1} onClick={() => setLineas(old => old.filter((_, i) => i !== index))} /></div>)}</div>
        <Button icon={Plus} disabled={busy || lineas.length >= (data?.productos.length || 0)} onClick={() => { const next = data?.productos.find(p => !lineas.some(l => l.stockId === p.id)); if (next) setLineas(old => [...old, { stockId: next.id, cantidad: 1 }]); }}>Agregar componente</Button></fieldset>}
      <ErrorMessage error={error} /></div><footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button variant="primary" type="submit" loading={busy} disabled={combo && !data?.productos.length}>{combo ? 'Crear combo' : 'Crear artículo'}</Button></footer></form>
  </Modal>;
}

export function PolicyForm({ product, onClose }: { product: Producto; onClose: () => void }) {
  const { data, busy, run } = useStore();
  const initial = data?.politicas[product.id];
  const [values, setValues] = useState<Omit<Politica, 'stockId'>>(initial ? (({ stockId: _, ...rest }) => rest)(initial) : {
    tipo: 'PUNTO_PEDIDO', puntoPedido: 20, stockObjetivo: 100, demandaDiaria: 0, diasCobertura: 1, factorEstacional: 1, umbralAlerta: 10, destinoAlerta: 'compras@coto.example',
  });
  const [error, setError] = useState<Error | null>(null);
  const setNumber = (name: keyof typeof values, value: string) => setValues(old => ({ ...old, [name]: value === '' ? '' : Number(value) }));
  const descriptions = { PUNTO_PEDIDO: 'Al alcanzar el punto de pedido, se repone hasta el stock objetivo.', DEMANDA: 'Se calcula la mercadería necesaria para cubrir la demanda diaria.', ESTACIONAL: 'El objetivo se ajusta por el factor de la temporada.' };
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    try { await run(() => api.guardarPolitica(product.id, values), 'Política de reposición guardada. Se aplicará en el próximo movimiento o evaluación.'); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return <Modal title="Política de reposición" description="Configurá cuándo y cuánto reponer." onClose={onClose}><form onSubmit={submit}><div className="modal-body form-stack"><ProductName product={product} />
    <Field label="Política"><select value={values.tipo} disabled={busy} onChange={e => setValues(old => ({ ...old, tipo: e.target.value as Politica['tipo'], puntoPedido: e.target.value === 'PUNTO_PEDIDO' ? Math.max(1, Number(old.puntoPedido)) : 0 }))}><option value="PUNTO_PEDIDO">Punto de pedido</option><option value="DEMANDA">Por demanda</option><option value="ESTACIONAL">Estacional</option></select></Field><p className="form-info">{descriptions[values.tipo]}</p>
    {values.tipo === 'PUNTO_PEDIDO' && <div className="form-grid"><Field label="Punto de pedido"><input required type="number" min="1" max="2147483646" step="1" value={values.puntoPedido} onChange={e => setNumber('puntoPedido', e.target.value)} disabled={busy} /></Field><Field label="Stock objetivo"><input required type="number" min={Number(values.puntoPedido) + 1} max="2147483647" step="1" value={values.stockObjetivo} onChange={e => setNumber('stockObjetivo', e.target.value)} disabled={busy} /></Field></div>}
    {values.tipo === 'DEMANDA' && <div className="form-grid"><Field label="Demanda diaria"><input required type="number" min="0" max="999999999.999" step="0.001" value={values.demandaDiaria} onChange={e => setNumber('demandaDiaria', e.target.value)} disabled={busy} /></Field><Field label="Días de cobertura"><input required type="number" min="1" max="2147483647" step="1" value={values.diasCobertura} onChange={e => setNumber('diasCobertura', e.target.value)} disabled={busy} /></Field></div>}
    {values.tipo === 'ESTACIONAL' && <div className="form-grid"><Field label="Objetivo base"><input required type="number" min="1" max="2147483647" step="1" value={values.stockObjetivo} onChange={e => setNumber('stockObjetivo', e.target.value)} disabled={busy} /></Field><Field label="Factor de temporada"><input required type="number" min="0.0001" max="99999999.9999" step="0.0001" value={values.factorEstacional} onChange={e => setNumber('factorEstacional', e.target.value)} disabled={busy} /></Field></div>}
    <div className="form-divider" /><Field label="Umbral de alerta" hint="Se envía un aviso cuando el disponible cae por debajo de este valor."><input required type="number" min="1" max="2147483647" step="1" value={values.umbralAlerta} onChange={e => setNumber('umbralAlerta', e.target.value)} disabled={busy} /></Field><Field label="Destino del aviso" hint="Email o teléfono, según el canal de notificación seleccionado."><input required maxLength={200} value={values.destinoAlerta} onChange={e => setValues(old => ({ ...old, destinoAlerta: e.target.value }))} disabled={busy} /></Field><ErrorMessage error={error} /></div>
    <footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button type="submit" variant="primary" loading={busy}>Guardar política</Button></footer></form></Modal>;
}

export function PromotionForm({ product, existing, nextOrder, onClose }: { product: Producto; existing?: Promocion; nextOrder: number; onClose: () => void }) {
  const { busy, run } = useStore();
  const [values, setValues] = useState({ tipo: existing?.tipo || 'DESCUENTO' as Promocion['tipo'], porcentaje: String(existing?.porcentaje ?? 10), orden: String(existing?.orden ?? nextOrder), activa: existing?.activa ?? true, medioPago: existing?.medioPago || 'DEBITO' });
  const [error, setError] = useState<Error | null>(null);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { tipo: values.tipo, porcentaje: Number(values.porcentaje), orden: Number(values.orden), activa: values.activa, medioPago: values.tipo === 'MEDIO_PAGO' ? values.medioPago.trim() : null };
    try { await run(() => api.guardarPromocion(product.id, body, existing?.id), 'Promoción guardada. Los precios del catálogo se actualizaron.'); onClose(); }
    catch (e) { setError(e as Error); }
  }
  return <Modal title={existing ? 'Editar promoción' : 'Nueva promoción'} description="Los descuentos se acumulan según su orden de aplicación." onClose={onClose}><form onSubmit={submit}><div className="modal-body form-stack"><ProductName product={product} />
    <Field label="Tipo de promoción"><select disabled={busy} value={values.tipo} onChange={e => setValues(old => ({ ...old, tipo: e.target.value as Promocion['tipo'], porcentaje: e.target.value === 'DOS_POR_UNO' ? '0' : old.porcentaje }))}><option value="DESCUENTO">Descuento porcentual</option><option value="DOS_POR_UNO">2×1</option><option value="MEDIO_PAGO">Descuento por medio de pago</option></select></Field>
    {values.tipo !== 'DOS_POR_UNO' ? <Field label="Descuento (%)"><input required type="number" min="0" max="100" step="0.0001" value={values.porcentaje} onChange={e => setValues(old => ({ ...old, porcentaje: e.target.value }))} disabled={busy} /></Field> : <p className="form-info">Cada dos unidades, se paga una. Las unidades impares se cobran a su precio correspondiente.</p>}
    {values.tipo === 'MEDIO_PAGO' && <Field label="Medio de pago"><input required maxLength={60} value={values.medioPago} onChange={e => setValues(old => ({ ...old, medioPago: e.target.value }))} disabled={busy} /></Field>}
    <Field label="Orden de aplicación" hint="Debe ser distinto al de las otras promociones del artículo."><input required type="number" min="0" max="2147483647" step="1" value={values.orden} onChange={e => setValues(old => ({ ...old, orden: e.target.value }))} disabled={busy} /></Field><label className="checkbox-label"><input type="checkbox" checked={values.activa} onChange={e => setValues(old => ({ ...old, activa: e.target.checked }))} disabled={busy} />Promoción activa</label><ErrorMessage error={error} /></div><footer className="modal-footer"><Button onClick={onClose} disabled={busy}>Cancelar</Button><Button type="submit" variant="primary" loading={busy}>Guardar promoción</Button></footer></form></Modal>;
}
