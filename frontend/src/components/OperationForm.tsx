import { useState, type FormEvent } from 'react';
import { ArrowRight, ArrowRightLeft, CheckCircle2, PackageCheck } from 'lucide-react';
import { api } from '../api';
import { movementLabels, number } from '../format';
import { useStore } from '../store';
import type { ResultadoMovimiento, TipoMovimiento } from '../types';
import { Button, ErrorMessage, Field, ProductName } from './ui';

const pdaTypes: TipoMovimiento[] = ['TRASLADO_A_GONDOLA', 'RETORNO_A_DEPOSITO', 'DEVOLUCION', 'MERMA_DEPOSITO', 'MERMA_GONDOLA'];
export function OperationForm({ mode, initialId, compact = false, onComplete, onCancel }: {
  mode: 'sucursal' | 'recepcion' | 'gestion-digital'; initialId?: number; compact?: boolean;
  onComplete?: (result: ResultadoMovimiento) => void; onCancel?: () => void;
}) {
  const { data, busy, error: connectionError, run } = useStore();
  const products = (data?.productos || []).filter(p => mode === 'gestion-digital' || p.tipo === 'SIMPLE');
  const [id, setId] = useState(initialId || products[0]?.id || 0);
  const [tipo, setTipo] = useState<TipoMovimiento>(mode === 'gestion-digital' ? 'RESERVA_DIGITAL' : 'TRASLADO_A_GONDOLA');
  const [cantidad, setCantidad] = useState('1');
  const [motivo, setMotivo] = useState(mode === 'recepcion' ? 'Recepción de mercadería' : '');
  const [usuario, setUsuario] = useState('operador.demo');
  const [error, setError] = useState<Error | null>(null);
  const [confirmed, setConfirmed] = useState<ResultadoMovimiento | null>(null);
  const product = products.find(p => p.id === id);
  const limit = mode === 'recepcion' ? 2147483647 - (product?.stockFisico || 0)
    : tipo === 'RESERVA_DIGITAL' ? product?.disponible
      : mode === 'gestion-digital' && product?.tipo === 'SIMPLE' ? product.stockReservado || 0
      : ['TRASLADO_A_GONDOLA', 'MERMA_DEPOSITO'].includes(tipo) ? product?.disponible
      : ['RETORNO_A_DEPOSITO', 'MERMA_GONDOLA'].includes(tipo) ? product?.stockExhibicion || 0 : 2147483647;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null);
    const body = { stockId: id, cantidad: Number(cantidad), motivo: motivo.trim(), usuario: usuario.trim() };
    try {
      const result = await run(() => mode === 'recepcion' ? api.ingresar(body) : mode === 'gestion-digital'
        ? api.digital(tipo === 'RESERVA_DIGITAL' ? 'reservas' : tipo === 'CANCELACION_RESERVA' ? 'cancelaciones' : 'despachos', body) : api.mover({ ...body, tipo }),
      r => `Movimiento #${r.id} confirmado. El inventario está actualizado.`);
      setConfirmed(result); setCantidad('1'); onComplete?.(result);
    } catch (e) { setError(e as Error); }
  }
  return <form onSubmit={submit} className={compact ? 'operation-form compact' : 'operation-form'}>
    <div className="operation-layout"><div className="form-stack">
      <Field label="Artículo"><select value={id} onChange={e => { setId(Number(e.target.value)); setConfirmed(null); }} required disabled={busy}>{products.length === 0 && <option value="">No hay artículos disponibles</option>}{products.map(p => <option key={p.id} value={p.id}>#{p.id} · {p.nombre}</option>)}</select></Field>
      {mode !== 'recepcion' && <Field label="Tipo de operación"><select value={tipo} onChange={e => setTipo(e.target.value as TipoMovimiento)} disabled={busy}>
        {(mode === 'sucursal' ? pdaTypes : ['RESERVA_DIGITAL', 'CANCELACION_RESERVA', 'DESPACHO_DIGITAL'] as TipoMovimiento[]).map(t => <option key={t} value={t}>{movementLabels[t]}</option>)}</select></Field>}
      {mode === 'gestion-digital' && product && <div className="digital-operation-context" aria-live="polite"><div className="split-line"><span>Disponible actual</span><strong>{number(product.disponible)}</strong></div><div className="split-line"><span>Reservado</span><strong>{product.tipo === 'COMBO' ? 'En sus componentes' : number(product.stockReservado || 0)}</strong></div><p className="field-hint">{tipo === 'RESERVA_DIGITAL' ? 'Compromete unidades y reduce el disponible.' : tipo === 'CANCELACION_RESERVA' ? 'Libera unidades reservadas y recupera el disponible.' : 'Descuenta físico y reservado; el disponible se mantiene.'}{product.tipo === 'COMBO' && ' El inventario valida las reservas de cada componente.'}</p></div>}
      <Field label="Cantidad" hint={mode === 'gestion-digital' && product?.tipo === 'COMBO' && tipo !== 'RESERVA_DIGITAL' ? 'Indicá la cantidad de combos previamente reservada. Se validan sus componentes.' : `En unidades enteras${mode !== 'recepcion' && limit !== undefined && limit < 2147483647 ? ` · máximo ${number(limit)}` : ''}.`}>
        <input type="number" min="1" max={limit} step="1" required value={cantidad} onChange={e => setCantidad(e.target.value)} disabled={busy} inputMode="numeric" /></Field>
      <Field label="Motivo"><input maxLength={500} required value={motivo} onChange={e => setMotivo(e.target.value)} placeholder="Describí el ingreso o movimiento" disabled={busy} /></Field>
      <Field label="Operador" hint="Se registra junto con el movimiento."><input maxLength={100} required value={usuario} onChange={e => setUsuario(e.target.value)} disabled={busy} /></Field>
      <ErrorMessage error={error} />
      {confirmed && !compact && <div className="message success" role="status"><CheckCircle2 size={24} aria-hidden="true" /><div><strong>Ingreso #{confirmed.id} registrado</strong><p>Disponible confirmado: {confirmed.disponibilidades[id]} unidades.</p></div></div>}
    </div>{!compact && product && <aside className="operation-preview"><span className="eyebrow">Artículo seleccionado</span><ProductName product={product} /><div className="preview-count"><span>Disponible actual</span><strong>{number(product.disponible)}</strong></div><div className="split-line"><span>Stock físico</span><strong>{number(product.stockFisico || 0)}</strong></div><div className="split-line"><span>En góndola</span><strong>{number(product.stockExhibicion || 0)}</strong></div><div className="split-line"><span>Reservado</span><strong>{number(product.stockReservado || 0)}</strong></div><div className="preview-note"><PackageCheck size={24} aria-hidden="true" /><p>El ingreso suma mercadería al depósito y actualiza la disponibilidad del artículo y sus combos.</p></div></aside>}</div>
    <div className={compact ? 'modal-footer' : 'form-actions'}>{onCancel && <Button onClick={onCancel} disabled={busy}>Cancelar</Button>}<Button type="submit" variant="primary" icon={mode === 'recepcion' ? ArrowRight : ArrowRightLeft} loading={busy} disabled={!product || !!connectionError || limit === 0}>
      {mode === 'recepcion' ? 'Registrar ingreso' : mode === 'gestion-digital' ? 'Confirmar operación' : 'Confirmar movimiento'}</Button></div>
  </form>;
}
