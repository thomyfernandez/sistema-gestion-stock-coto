import { useState, type FormEvent } from 'react';
import { ArrowRight, ArrowRightLeft, CheckCircle2, PackageCheck } from 'lucide-react';
import { api } from '../api';
import { movementLabels, number } from '../format';
import { MAX_INT_32 } from '../limites';
import { useStore } from '../store';
import type { Operacion, Producto, ResultadoMovimiento, TipoMovimiento } from '../types';
import { Button, ErrorMessage, Field, OperadorSesion, ProductName } from './ui';

type Modo = 'sucursal' | 'recepcion' | 'gestion-digital';
const pdaTypes: TipoMovimiento[] = ['TRASLADO_A_GONDOLA', 'RETORNO_A_DEPOSITO', 'DEVOLUCION', 'MERMA_DEPOSITO', 'MERMA_GONDOLA'];
const tiposDigitales: TipoMovimiento[] = ['RESERVA_DIGITAL', 'CANCELACION_RESERVA', 'DESPACHO_DIGITAL'];
const etiquetaConfirmar: Record<Modo, string> = { recepcion: 'Registrar ingreso', 'gestion-digital': 'Confirmar operación', sucursal: 'Confirmar movimiento' };

/** Máximo que acepta el inventario para la operación elegida; sin artículo cargado, el navegador no impone límite. */
function limiteOperacion(mode: Modo, tipo: TipoMovimiento, product?: Producto): number | undefined {
  if (mode === 'recepcion') return MAX_INT_32 - (product?.stockFisico || 0);
  if (tipo === 'RESERVA_DIGITAL') return product?.disponible;
  if (mode === 'gestion-digital' && product?.tipo === 'SIMPLE') return product.stockReservado || 0;
  if (tipo === 'TRASLADO_A_GONDOLA' || tipo === 'MERMA_DEPOSITO') return product?.disponible;
  if (tipo === 'RETORNO_A_DEPOSITO' || tipo === 'MERMA_GONDOLA') return product?.stockExhibicion || 0;
  return MAX_INT_32;
}

function accionDigital(tipo: TipoMovimiento) {
  if (tipo === 'RESERVA_DIGITAL') return 'reservas';
  if (tipo === 'CANCELACION_RESERVA') return 'cancelaciones';
  return 'despachos';
}

function efectoDigital(tipo: TipoMovimiento) {
  if (tipo === 'RESERVA_DIGITAL') return 'Compromete unidades y reduce el disponible.';
  if (tipo === 'CANCELACION_RESERVA') return 'Libera unidades reservadas y recupera el disponible.';
  return 'Descuenta físico y reservado; el disponible se mantiene.';
}

function pistaCantidad(mode: Modo, tipo: TipoMovimiento, product: Producto | undefined, limit: number | undefined) {
  if (mode === 'gestion-digital' && product?.tipo === 'COMBO' && tipo !== 'RESERVA_DIGITAL') return 'Indicá la cantidad de combos previamente reservada. Se validan sus componentes.';
  const maximo = mode !== 'recepcion' && limit !== undefined && limit < MAX_INT_32 ? ` · máximo ${number(limit)}` : '';
  return `En unidades enteras${maximo}.`;
}

function registrar(mode: Modo, tipo: TipoMovimiento, body: Operacion) {
  if (mode === 'recepcion') return api.ingresar(body);
  if (mode === 'gestion-digital') return api.digital(accionDigital(tipo), body);
  return api.mover({ ...body, tipo });
}

export function OperationForm({ mode, initialId, compact = false, onComplete, onCancel }: {
  mode: Modo; initialId?: number; compact?: boolean;
  onComplete?: (result: ResultadoMovimiento) => void; onCancel?: () => void;
}) {
  const { data, busy, error: connectionError, run } = useStore();
  const products = (data?.productos || []).filter(p => mode === 'gestion-digital' || p.tipo === 'SIMPLE');
  const [id, setId] = useState(initialId || products[0]?.id || 0);
  const [tipo, setTipo] = useState<TipoMovimiento>(mode === 'gestion-digital' ? 'RESERVA_DIGITAL' : 'TRASLADO_A_GONDOLA');
  const [cantidad, setCantidad] = useState('1');
  const [motivo, setMotivo] = useState(mode === 'recepcion' ? 'Recepción de mercadería' : '');
  const [error, setError] = useState<Error | null>(null);
  const [confirmed, setConfirmed] = useState<ResultadoMovimiento | null>(null);
  const product = products.find(p => p.id === id);
  const limit = limiteOperacion(mode, tipo, product);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { stockId: id, cantidad: Number(cantidad), motivo: motivo.trim() };
    try {
      const result = await run(() => registrar(mode, tipo, body), r => `Movimiento #${r.id} confirmado. El inventario está actualizado.`);
      setConfirmed(result); setCantidad('1'); onComplete?.(result);
    } catch (e) { setError(e as Error); }
  }
  return <form onSubmit={submit} className={compact ? 'operation-form compact' : 'operation-form'}>
    <div className="operation-layout"><div className="form-stack">
      <Field label="Artículo"><select value={id} onChange={e => { setId(Number(e.target.value)); setConfirmed(null); }} required disabled={busy}>{products.length === 0 && <option value="">No hay artículos disponibles</option>}{products.map(p => <option key={p.id} value={p.id}>#{p.id} · {p.nombre}</option>)}</select></Field>
      {mode !== 'recepcion' && <Field label="Tipo de operación"><select value={tipo} onChange={e => setTipo(e.target.value as TipoMovimiento)} disabled={busy}>
        {(mode === 'sucursal' ? pdaTypes : tiposDigitales).map(t => <option key={t} value={t}>{movementLabels[t]}</option>)}</select></Field>}
      {mode === 'gestion-digital' && product && <div className="digital-operation-context" aria-live="polite"><div className="split-line"><span>Disponible actual</span><strong>{number(product.disponible)}</strong></div><div className="split-line"><span>Reservado</span><strong>{product.tipo === 'COMBO' ? 'En sus componentes' : number(product.stockReservado || 0)}</strong></div><p className="field-hint">{efectoDigital(tipo)}{product.tipo === 'COMBO' && ' El inventario valida las reservas de cada componente.'}</p></div>}
      <Field label="Cantidad" hint={pistaCantidad(mode, tipo, product, limit)}>
        <input type="number" min="1" max={limit} step="1" required value={cantidad} onChange={e => setCantidad(e.target.value)} disabled={busy} inputMode="numeric" /></Field>
      <Field label="Motivo"><input maxLength={500} required value={motivo} onChange={e => setMotivo(e.target.value)} placeholder="Describí el ingreso o movimiento" disabled={busy} /></Field>
      <OperadorSesion hint="Se registra junto con el movimiento." />
      <ErrorMessage error={error} />
      {confirmed && !compact && <div className="message success" role="status"><CheckCircle2 size={24} aria-hidden="true" /><div><strong>Ingreso #{confirmed.id} registrado</strong><p>Disponible confirmado: {confirmed.disponibilidades[id]} unidades.</p></div></div>}
    </div>{!compact && product && <aside className="operation-preview"><span className="eyebrow">Artículo seleccionado</span><ProductName product={product} /><div className="preview-count"><span>Disponible actual</span><strong>{number(product.disponible)}</strong></div><div className="split-line"><span>Stock físico</span><strong>{number(product.stockFisico || 0)}</strong></div><div className="split-line"><span>En góndola</span><strong>{number(product.stockExhibicion || 0)}</strong></div><div className="split-line"><span>Reservado</span><strong>{number(product.stockReservado || 0)}</strong></div><div className="preview-note"><PackageCheck size={24} aria-hidden="true" /><p>El ingreso suma mercadería al depósito y actualiza la disponibilidad del artículo y sus combos.</p></div></aside>}</div>
    <div className={compact ? 'modal-footer' : 'form-actions'}>{onCancel && <Button onClick={onCancel} disabled={busy}>Cancelar</Button>}<Button type="submit" variant="primary" icon={mode === 'recepcion' ? ArrowRight : ArrowRightLeft} loading={busy} disabled={!product || !!connectionError || limit === 0}>
      {etiquetaConfirmar[mode]}</Button></div>
  </form>;
}
