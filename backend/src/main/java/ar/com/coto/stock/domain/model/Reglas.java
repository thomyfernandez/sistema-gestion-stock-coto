package ar.com.coto.stock.domain.model;

/** Validaciones compartidas del dominio, sin dependencias de infraestructura. */
public final class Reglas {
    private Reglas() { }

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

    public static double porcentaje(double valor) {
        noNegativo(valor, "porcentaje");
        if (valor > 100) throw new IllegalArgumentException("porcentaje no puede superar 100");
        return valor;
    }

    public static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return valor.strip();
    }
}
