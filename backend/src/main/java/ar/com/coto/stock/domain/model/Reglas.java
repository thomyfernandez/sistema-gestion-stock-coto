package ar.com.coto.stock.domain.model;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Validaciones compartidas del dominio, sin dependencias de infraestructura. */
public final class Reglas {
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern TELEFONO = Pattern.compile("\\+?[0-9][0-9 ()-]{5,29}");
    private static final int MAX_EMAIL = 200;
    private static final int MAX_TELEFONO = 30;

    private Reglas() { }

    public static boolean esEmail(String valor) {
        return valor != null && valor.length() <= MAX_EMAIL && EMAIL.matcher(valor).matches();
    }

    public static boolean esTelefono(String valor) {
        return valor != null && valor.length() <= MAX_TELEFONO && TELEFONO.matcher(valor).matches();
    }

    public static int positivo(int valor, String campo) {
        if (valor <= 0) throw new IllegalArgumentException(campo + " debe ser positivo");
        return valor;
    }

    public static int noNegativo(int valor, String campo) {
        if (valor < 0) throw new IllegalArgumentException(campo + " no puede ser negativo");
        return valor;
    }

    public static double noNegativo(double valor, String campo) {
        if (!Double.isFinite(valor) || valor < 0) {
            throw new IllegalArgumentException(campo + " debe ser finito y no negativo");
        }
        return valor;
    }

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static BigDecimal noNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException(campo + " es obligatorio y no puede ser negativo");
        return valor;
    }

    public static BigDecimal porcentaje(BigDecimal valor) {
        noNegativo(valor, "porcentaje");
        if (valor.compareTo(CIEN) > 0) throw new IllegalArgumentException("porcentaje no puede superar 100");
        return valor;
    }

    public static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return valor.strip();
    }
}
