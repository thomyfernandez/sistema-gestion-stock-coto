import type { Politica, Producto, TipoMovimiento } from './types';
export const money = (value: number) => new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 2 }).format(value);
export const number = (value: number) => new Intl.NumberFormat('es-AR').format(value);
export const date = (value: string) => new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date(value));
export const sku = (id: number) => `#${String(id).padStart(4, '0')}`;
export const movementLabels: Record<TipoMovimiento, string> = {
  RECEPCION: 'Recepción', TRASLADO_A_GONDOLA: 'Traslado a góndola', RETORNO_A_DEPOSITO: 'Retorno a depósito',
  DEVOLUCION: 'Devolución', MERMA_DEPOSITO: 'Merma en depósito', MERMA_GONDOLA: 'Merma en góndola',
  RESERVA_DIGITAL: 'Reserva digital', CANCELACION_RESERVA: 'Cancelación de reserva', DESPACHO_DIGITAL: 'Despacho digital',
  ARMADO_COMBO: 'Armado de combo', DESARMADO_COMBO: 'Desarmado de combo',
};
export function stockStatus(p: Producto, politica?: Politica) {
  if (p.disponible === 0) return { label: 'Sin disponible', tone: 'danger' as const };
  if (politica && p.disponible < politica.umbralAlerta) return { label: 'Stock bajo', tone: 'warning' as const };
  return { label: 'Disponible', tone: 'success' as const };
}

export const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
/** Días que puede tener el mes en algún año (febrero, 29). */
export const diasDelMes = (mes: number) => [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mes - 1] ?? 31;
const dosCifras = (n: number) => String(n).padStart(2, '0');
export const diaMes = (dia: number, mes: number) => `${dosCifras(dia)}/${dosCifras(mes)}`;
type Periodo = { desdeDia: number; desdeMes: number; hastaDia: number; hastaMes: number };
export const cruzaElAnio = (p: Periodo) => p.desdeMes > p.hastaMes || (p.desdeMes === p.hastaMes && p.desdeDia > p.hastaDia);
export const todoElAnio = (p: Periodo) => p.desdeDia === 1 && p.desdeMes === 1 && p.hastaDia === 31 && p.hastaMes === 12;
export const periodoCorto = (p: Periodo) => `${diaMes(p.desdeDia, p.desdeMes)} al ${diaMes(p.hastaDia, p.hastaMes)}`;
const fechaLarga = (dia: number, mes: number) => `${dia} de ${meses[mes - 1].toLowerCase()}`;
/** Resumen en palabras del período; aclara el cruce de año y el 29 de febrero. */
export function periodoLargo(p: Periodo) {
  let texto: string;
  if (todoElAnio(p)) texto = 'Todo el año: del 1 de enero al 31 de diciembre.';
  else if (p.desdeDia === p.hastaDia && p.desdeMes === p.hastaMes) texto = `Sólo el ${fechaLarga(p.desdeDia, p.desdeMes)} de cada año.`;
  else if (cruzaElAnio(p)) texto = `Del ${fechaLarga(p.desdeDia, p.desdeMes)} al ${fechaLarga(p.hastaDia, p.hastaMes)} del año siguiente. Cruza el fin de año.`;
  else texto = `Del ${fechaLarga(p.desdeDia, p.desdeMes)} al ${fechaLarga(p.hastaDia, p.hastaMes)} de cada año.`;
  if (p.hastaDia === 29 && p.hastaMes === 2) texto += ' En los años no bisiestos termina el 28 de febrero.';
  if (p.desdeDia === 29 && p.desdeMes === 2) texto += ' En los años no bisiestos empieza el 1 de marzo.';
  return texto;
}
const decimal = (value: number) => new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 }).format(value);
export const factor = (f: number) => `×${decimal(f)}`;
export function ajuste(f: number) {
  if (f === 1) return 'Sin ajuste';
  const porcentaje = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(Math.abs(Math.round((f - 1) * 10000) / 100));
  return `${porcentaje} % ${f > 1 ? 'más' : 'menos'}`;
}
/** Vista previa de ceil(base × factor). El factor tiene hasta 4 decimales: se opera en diezmilésimos para
 * evitar errores de coma flotante (100 × 1,1 no debe dar 111). El valor real lo calcula el backend. */
export const objetivoConFactor = (base: number, f: number) => Math.ceil(base * Math.round(f * 10000) / 10000);
/** "2026-11-26" (fecha de la sucursal que informa el backend) → "26/11/2026", sin conversiones de zona. */
export const fechaDia = (iso: string) => iso.split('-').reverse().join('/');
