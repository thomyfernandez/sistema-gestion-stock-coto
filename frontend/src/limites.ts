/** Límites de los campos numéricos: replican las columnas del esquema para que el formulario rechace antes que el backend. */

/** Columnas INTEGER: cantidades, umbrales, órdenes y días. */
export const MAX_INT_32 = 2_147_483_647;
/** El punto de pedido deja lugar a un stock objetivo estrictamente mayor. */
export const MAX_PUNTO_PEDIDO = MAX_INT_32 - 1;
/** Precio de lista: NUMERIC(15, 2). */
export const MAX_PRECIO = 9_999_999_999_999.99;
/** Demanda diaria: NUMERIC(12, 3). */
export const MAX_DEMANDA_DIARIA = 999_999_999.999;
/** Objetivo base de la política estacional: por el factor máximo de una temporada (10) tiene que entrar en un INTEGER. */
export const MAX_OBJETIVO_ESTACIONAL = 214_748_364;
/** Factor de una temporada de reposición: NUMERIC(6, 4), mayor que 0 y hasta 10. */
export const MAX_FACTOR_TEMPORADA = 10;
/** Imagen de artículo: hasta 2 MB. */
export const MAX_IMAGEN_BYTES = 2 * 1024 * 1024;
