export interface Componente { stockId: number; nombre: string; tipo: 'SIMPLE' | 'COMBO'; cantidadEnCombo: number; disponible: number }
export interface Producto {
  id: number; categoriaId: number | null; nombre: string; descripcion: string; precio: number; tipo: 'SIMPLE' | 'COMBO'; disponible: number;
  stockFisico: number | null; stockExhibicion: number | null; stockReservado: number | null; componentes: Componente[]; imagenUrl: string | null;
}
export type EstadoPedido = 'PENDIENTE' | 'ENVIADO' | 'RECIBIDO' | 'CANCELADO';
export interface Pedido { id: number; stockId: number; fecha: string; cantidad: number; estado: EstadoPedido }
export type TipoMovimiento = 'RECEPCION' | 'TRASLADO_A_GONDOLA' | 'RETORNO_A_DEPOSITO' | 'DEVOLUCION' | 'MERMA_DEPOSITO' | 'MERMA_GONDOLA' | 'RESERVA_DIGITAL' | 'CANCELACION_RESERVA' | 'DESPACHO_DIGITAL';
export interface Movimiento { id: number; stockId: number; tipo: TipoMovimiento; cantidad: number; fecha: string; motivo: string; usuario: string }
export interface Operacion { stockId: number; cantidad: number; motivo: string; usuario: string }
export interface ResultadoMovimiento { id: number; disponibilidades: Record<string, number>; pedidosGenerados: Pedido[] }
export interface Auditoria { stockId: number; fecha: string; disponibleAnterior: number | null; disponibleActual: number; detalle: string }
export interface Politica {
  stockId: number; tipo: 'PUNTO_PEDIDO' | 'DEMANDA' | 'ESTACIONAL'; puntoPedido: number; stockObjetivo: number;
  demandaDiaria: number; diasCobertura: number; factorEstacional: number; umbralAlerta: number; destinoAlerta: string; contactoId: number | null; canalAlerta: Canal | null;
}
export interface Promocion { id: number; productoId: number; tipo: 'DESCUENTO' | 'DOS_POR_UNO' | 'MEDIO_PAGO'; porcentaje: number; orden: number; activa: boolean; medioPago: string | null }
export interface Precio { productoId: number; cantidad: number; precioLista: number; precioFinal: number; ahorro: number; promocionesAplicadas: number[] }
export interface ItemCatalogo { id: number; nombre: string; descripcion: string; tipo: 'SIMPLE' | 'COMBO'; disponible: number; precio: Precio }
export type Canal = 'EMAIL' | 'SMS' | 'WHATSAPP';
export type Sistema = 'ERP' | 'MENSAJERIA' | 'COTO_DIGITAL';
export interface Evento { id: number; tipo: 'STOCK_DIGITAL' | 'ALERTA' | 'PEDIDO_ERP'; stockId: number; pedidoId: number | null; estado: 'PENDIENTE' | 'ENVIADO'; intentos: number; ultimoError: string | null; fecha: string }
export interface Integraciones {
  entregas: Evento[]; proveedoresDisponibles: Record<Sistema, boolean>;
  mensajes: { canal: Canal; destino: string; texto: string }[];
  pedidosERP: { referencia: string; productoId: number; cantidad: number; fecha: string }[];
  catalogoDigital: Record<string, string>;
}
export interface Contacto { id: number; nombre: string; email: string; telefono: string }
export interface Categoria { id: number; nombre: string; contactoId: number | null; canalAlerta: Canal | null }
export interface Snapshot { categorias: Categoria[]; contactos: Contacto[]; avisos: ConfiguracionAvisos; productos: Producto[]; pedidos: Pedido[]; movimientos: Movimiento[]; auditoria: Auditoria[]; integraciones: Integraciones; politicas: Record<number, Politica> }

export interface ConfiguracionAvisos { canal: Canal; email: string; telefono: string }
export interface ImagenCarga { tipo: string; contenido: string }
