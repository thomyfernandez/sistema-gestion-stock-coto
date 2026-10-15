import type { Politica, Producto, TipoMovimiento } from './types';
export const money = (value: number) => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 2 }).format(value);
export const number = (value: number) => new Intl.NumberFormat('es-AR').format(value);
export const date = (value: string) => new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date(value));
export const sku = (id: number) => `#${String(id).padStart(4, '0')}`;
export const movementLabels: Record<TipoMovimiento, string> = {
  RECEPCION: 'Recepción', TRASLADO_A_GONDOLA: 'Traslado a góndola', RETORNO_A_DEPOSITO: 'Retorno a depósito',
  DEVOLUCION: 'Devolución', MERMA_DEPOSITO: 'Merma en depósito', MERMA_GONDOLA: 'Merma en góndola',
  RESERVA_DIGITAL: 'Reserva digital', CANCELACION_RESERVA: 'Cancelación de reserva', DESPACHO_DIGITAL: 'Despacho digital',
};
export function stockStatus(p: Producto, politica?: Politica) {
  if (p.disponible === 0) return { label: 'Sin disponible', tone: 'danger' as const };
  if (politica && p.disponible < politica.umbralAlerta) return { label: 'Stock bajo', tone: 'warning' as const };
  return { label: 'Disponible', tone: 'success' as const };
}
