import type { Promocion } from './types';

/** Medios de pago que ofrecen la simulación y las promociones por medio de pago. */
export const MEDIOS_PAGO = [
  { valor: 'DEBITO', etiqueta: 'Débito' },
  { valor: 'CREDITO', etiqueta: 'Crédito' },
  { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
] as const;
export type MedioPago = typeof MEDIOS_PAGO[number]['valor'];
export const MEDIO_PAGO_POR_DEFECTO: MedioPago = 'DEBITO';

/** Un medio cargado antes de que existiera la lista se muestra tal como se guardó. */
export function etiquetaMedioPago(valor: string | null) {
  return MEDIOS_PAGO.find(m => m.valor === valor)?.etiqueta ?? valor ?? '';
}

/** Texto principal y aclaración de una promoción, iguales en el listado y en la confirmación de borrado. */
export function describirPromocion(p: Pick<Promocion, 'tipo' | 'porcentaje' | 'medioPago'>): { titulo: string; detalle: string } {
  switch (p.tipo) {
    case 'DOS_POR_UNO':
      return { titulo: '2×1 en unidades', detalle: 'Cada dos unidades, pagás una' };
    case 'MEDIO_PAGO': {
      const medio = etiquetaMedioPago(p.medioPago);
      return { titulo: `${p.porcentaje}% de descuento con ${medio}`, detalle: `Aplica pagando con ${medio}` };
    }
    case 'DESCUENTO':
      return { titulo: `${p.porcentaje}% de descuento`, detalle: 'Sobre el importe acumulado según orden' };
  }
}
