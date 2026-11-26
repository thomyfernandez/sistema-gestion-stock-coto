package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.Producto;
import ar.com.coto.stock.domain.model.StockSimple;
import ar.com.coto.stock.domain.reposicion.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Temporadas que se repiten cada año, resolución artículo → categoría → 1 y la fecha tomada del reloj. */
class TemporadasTest {
    private static final ZoneId SUCURSAL = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int PAN_DULCE = 5;
    private static final int ALMACEN = 1;

    private static Temporada deArticulo(String nombre, int desdeDia, int desdeMes, int hastaDia, int hastaMes, String factor) {
        return new Temporada(0, nombre, List.of(PAN_DULCE), null, MonthDay.of(desdeMes, desdeDia), MonthDay.of(hastaMes, hastaDia), new BigDecimal(factor));
    }
    private static Temporada deCategoria(String nombre, int desdeDia, int desdeMes, int hastaDia, int hastaMes, String factor) {
        return new Temporada(0, nombre, List.of(), ALMACEN, MonthDay.of(desdeMes, desdeDia), MonthDay.of(hastaMes, hastaDia), new BigDecimal(factor));
    }
    private static Clock el(int dia, int mes, int anio) {
        return Clock.fixed(LocalDate.of(anio, mes, dia).atTime(12, 0).atZone(SUCURSAL).toInstant(), SUCURSAL);
    }
    private static StockSimple panDulce(int disponible) {
        return new StockSimple(new Producto(PAN_DULCE, "Pan dulce", "COTO 500 g", 5490, null, ALMACEN), disponible, 0, 0);
    }

    @Test void temporadaDentroDelAnioIncluyeSusLimites() {
        Temporada vuelta = deArticulo("Vuelta al cole", 15, 2, 15, 3, "1.2");
        assertFalse(vuelta.cruzaElAnio());
        assertTrue(vuelta.incluye(LocalDate.of(2027, 2, 15)));
        assertTrue(vuelta.incluye(LocalDate.of(2027, 3, 1)));
        assertTrue(vuelta.incluye(LocalDate.of(2027, 3, 15)));
        assertFalse(vuelta.incluye(LocalDate.of(2027, 2, 14)));
        assertFalse(vuelta.incluye(LocalDate.of(2027, 3, 16)));
    }

    @Test void temporadaDeDiciembreAEneroCruzaElAnio() {
        Temporada navidad = deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5");
        assertTrue(navidad.cruzaElAnio());
        assertTrue(navidad.incluye(LocalDate.of(2026, 11, 15)));
        assertTrue(navidad.incluye(LocalDate.of(2026, 12, 20)));
        assertTrue(navidad.incluye(LocalDate.of(2026, 12, 31)));
        assertTrue(navidad.incluye(LocalDate.of(2027, 1, 1)));
        assertTrue(navidad.incluye(LocalDate.of(2027, 1, 3)));
        assertTrue(navidad.incluye(LocalDate.of(2027, 1, 6)));
        assertFalse(navidad.incluye(LocalDate.of(2027, 1, 7)));
        assertFalse(navidad.incluye(LocalDate.of(2027, 1, 10)));
        assertFalse(navidad.incluye(LocalDate.of(2026, 11, 14)));
        assertFalse(navidad.incluye(LocalDate.of(2026, 7, 9)));
    }

    @Test void finDeFebreroBisiestoTerminaEl28EnLosDemasAnios() {
        Temporada febrero = deArticulo("Febrero", 1, 2, 29, 2, "1.1");
        assertTrue(febrero.incluye(LocalDate.of(2028, 2, 29)));
        assertTrue(febrero.incluye(LocalDate.of(2027, 2, 28)));
        assertFalse(febrero.incluye(LocalDate.of(2027, 3, 1)));
    }

    @Test void superposicionContemplaElCruceDeAnio() {
        Temporada navidad = deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5");
        assertTrue(navidad.superponeCon(deArticulo("Verano", 1, 1, 28, 2, "1.2")));
        assertTrue(navidad.superponeCon(deArticulo("Fiestas", 20, 12, 31, 12, "2")));
        assertFalse(navidad.superponeCon(deArticulo("Verano", 7, 1, 28, 2, "1.2")));
        assertFalse(navidad.superponeCon(deArticulo("Día del padre", 1, 6, 20, 6, "1.3")));
        assertTrue(navidad.comparteAlcance(deArticulo("Otra", 1, 6, 2, 6, "1")));
        assertFalse(navidad.comparteAlcance(deCategoria("Otra", 1, 6, 2, 6, "1")));
    }

    @Test void validaAlcanceFactorYNombre() {
        MonthDay enero = MonthDay.of(1, 1);
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Ambos", List.of(5), 1, enero, enero, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Ninguno", List.of(), null, enero, enero, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, " ", List.of(5), null, enero, enero, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "x".repeat(81), List.of(5), null, enero, enero, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Cero", List.of(5), null, enero, enero, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Negativo", List.of(5), null, enero, enero, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Excesivo", List.of(5), null, enero, enero, new BigDecimal("10.0001")));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Decimales", List.of(5), null, enero, enero, new BigDecimal("1.00001")));
        assertEquals("Diez", new Temporada(0, " Diez ", List.of(5), null, enero, enero, new BigDecimal("10.0000")).nombre());
    }

    @Test void variosArticulosSeNormalizanYComparteAlcanceQuienTengaUnoEnComun() {
        MonthDay enero = MonthDay.of(1, 1), febrero = MonthDay.of(2, 28);
        var fiestas = new Temporada(0, "Fiestas", List.of(7, 5, 5, 9), null, enero, febrero, BigDecimal.ONE);
        assertEquals(List.of(5, 7, 9), fiestas.stockIds());
        assertTrue(fiestas.incluyeArticulo(7));
        assertFalse(fiestas.incluyeArticulo(6));
        var otra = new Temporada(0, "Otra", List.of(3, 9), null, enero, febrero, BigDecimal.ONE);
        assertEquals(List.of(9), fiestas.articulosEnComun(otra));
        assertTrue(fiestas.comparteAlcance(otra));
        assertFalse(fiestas.comparteAlcance(new Temporada(0, "Sin común", List.of(1, 2), null, enero, febrero, BigDecimal.ONE)));
        assertFalse(fiestas.comparteAlcance(deCategoria("Almacén", 1, 1, 28, 2, "1")));
        assertTrue(deCategoria("A", 1, 1, 2, 1, "1").comparteAlcance(deCategoria("B", 1, 6, 2, 6, "1")));
        // Cada artículo de la temporada la ve en su calendario.
        for (int id : List.of(5, 7, 9)) {
            assertEquals(CalendarioTemporadas.Origen.ARTICULO, CalendarioTemporadas.para(id, null, List.of(fiestas)).factorPara(LocalDate.of(2027, 1, 20)).origen());
        }
        assertEquals(CalendarioTemporadas.Origen.SIN_TEMPORADA, CalendarioTemporadas.para(6, null, List.of(fiestas)).factorPara(LocalDate.of(2027, 1, 20)).origen());
        var muchos = java.util.stream.IntStream.rangeClosed(1, Temporada.MAXIMO_ARTICULOS + 1).boxed().toList();
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Demasiados", muchos, null, enero, febrero, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Temporada(0, "Id inválido", List.of(0), null, enero, febrero, BigDecimal.ONE));
    }

    @Test void laTemporadaDelArticuloPrevaleceSobreLaDeSuCategoria() {
        var calendario = CalendarioTemporadas.para(PAN_DULCE, ALMACEN, List.of(
                deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5"),
                deCategoria("Fiestas almacén", 1, 12, 31, 12, "1.2"),
                deCategoria("Invierno almacén", 1, 6, 31, 8, "1.1"),
                new Temporada(0, "De otro artículo", List.of(9), null, MonthDay.of(1, 1), MonthDay.of(12, 31), new BigDecimal("3"))));
        var diciembre = calendario.factorPara(LocalDate.of(2026, 12, 20));
        assertEquals(CalendarioTemporadas.Origen.ARTICULO, diciembre.origen());
        assertEquals("Navidad y Reyes", diciembre.temporada().nombre());
        assertEquals(0, new BigDecimal("1.5").compareTo(diciembre.factor()));
        var julio = calendario.factorPara(LocalDate.of(2026, 7, 9));
        assertEquals(CalendarioTemporadas.Origen.CATEGORIA, julio.origen());
        assertEquals(0, new BigDecimal("1.1").compareTo(julio.factor()));
        var marzo = calendario.factorPara(LocalDate.of(2026, 3, 10));
        assertEquals(CalendarioTemporadas.Origen.SIN_TEMPORADA, marzo.origen());
        assertNull(marzo.temporada());
        assertEquals(0, BigDecimal.ONE.compareTo(marzo.factor()));
        assertEquals(0, new BigDecimal("1.5").compareTo(calendario.factorMaximo()));
    }

    @Test void sinCategoriaSoloCuentanLasTemporadasDelArticulo() {
        var calendario = CalendarioTemporadas.para(PAN_DULCE, null, List.of(deCategoria("Invierno almacén", 1, 6, 31, 8, "1.1")));
        assertEquals(CalendarioTemporadas.Origen.SIN_TEMPORADA, calendario.factorPara(LocalDate.of(2026, 7, 9)).origen());
        assertEquals(CalendarioTemporadas.Origen.SIN_TEMPORADA, CalendarioTemporadas.vacio().factorPara(LocalDate.of(2026, 7, 9)).origen());
    }

    @Test void estacionalUsaElFactorDeLaTemporadaSoloDentroDeElla() {
        var calendario = CalendarioTemporadas.para(PAN_DULCE, ALMACEN, List.of(deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5")));
        // Objetivo 40: en temporada ceil(40 × 1,5) = 60; fuera de ella, 40. Disponible 30.
        assertEquals(30, new ReposicionEstacional(40, calendario, el(20, 12, 2026)).calcularCantidad(panDulce(30)));
        assertEquals(30, new ReposicionEstacional(40, calendario, el(3, 1, 2027)).calcularCantidad(panDulce(30)));
        assertEquals(10, new ReposicionEstacional(40, calendario, el(10, 1, 2027)).calcularCantidad(panDulce(30)));
        assertEquals(10, new ReposicionEstacional(40, calendario, el(14, 11, 2026)).calcularCantidad(panDulce(30)));
        assertEquals(0, new ReposicionEstacional(40, calendario, el(10, 3, 2026)).calcularCantidad(panDulce(45)));
    }

    @Test void elDiaSeDecideConLaZonaDelReloj() {
        var calendario = CalendarioTemporadas.para(PAN_DULCE, null, List.of(deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5")));
        // 15/11 02:00 UTC todavía es 14/11 en la sucursal: no empezó la temporada.
        Instant instante = Instant.parse("2026-11-15T02:00:00Z");
        assertEquals(10, new ReposicionEstacional(40, calendario, Clock.fixed(instante, SUCURSAL)).calcularCantidad(panDulce(30)));
        assertEquals(30, new ReposicionEstacional(40, calendario, Clock.fixed(instante, ZoneOffset.UTC)).calcularCantidad(panDulce(30)));
    }

    @Test void politicaEstacionalArmaLaEstrategiaConCalendarioYReloj() {
        var politica = new PoliticaReposicion(PAN_DULCE, PoliticaReposicion.Tipo.ESTACIONAL, 0, 40, 0, 1, 10, "compras@coto.example");
        var calendario = CalendarioTemporadas.para(PAN_DULCE, ALMACEN, List.of(deArticulo("Navidad y Reyes", 15, 11, 6, 1, "1.5")));
        assertEquals(30, politica.crearEstrategia(calendario, el(24, 12, 2026)).calcularCantidad(panDulce(30)));
        assertEquals(10, politica.crearEstrategia(calendario, el(1, 4, 2026)).calcularCantidad(panDulce(30)));
        assertEquals(10, politica.crearEstrategia(CalendarioTemporadas.vacio(), el(24, 12, 2026)).calcularCantidad(panDulce(30)));
        // Con el factor máximo de una temporada (10) el objetivo tiene que seguir entrando en un int.
        assertThrows(ArithmeticException.class, () -> new PoliticaReposicion(PAN_DULCE, PoliticaReposicion.Tipo.ESTACIONAL,
                0, 300_000_000, 0, 1, 10, "compras@coto.example"));
        assertDoesNotThrow(() -> new PoliticaReposicion(PAN_DULCE, PoliticaReposicion.Tipo.PUNTO_PEDIDO,
                20, 300_000_000, 0, 1, 10, "compras@coto.example"));
    }

    @Test void factorFijoSeMantieneComoAntesCualquierDia() {
        var fijo = new ReposicionEstacional(40, 1.5);
        assertEquals(CalendarioTemporadas.Origen.FIJO, fijo.factorVigente().origen());
        assertEquals(30, fijo.calcularCantidad(panDulce(30)));
    }
}
