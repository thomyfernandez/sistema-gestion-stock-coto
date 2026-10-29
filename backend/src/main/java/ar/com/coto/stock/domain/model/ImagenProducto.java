package ar.com.coto.stock.domain.model;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Imagen raster opcional. Nunca admite HTML/SVG ni rutas de archivos del cliente. */
public record ImagenProducto(String tipo, byte[] contenido) {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public ImagenProducto {
        if (contenido == null || contenido.length == 0 || contenido.length > MAX_BYTES) throw new IllegalArgumentException("La imagen debe pesar hasta 2 MB");
        if (tipo == null || !java.util.Set.of("image/png", "image/jpeg").contains(tipo)) throw new IllegalArgumentException("La imagen debe ser PNG o JPEG");
        contenido = Arrays.copyOf(contenido, contenido.length);
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("El archivo no contiene una imagen válida");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(tipo.equals("image/png") && format.equalsIgnoreCase("png")) && !(tipo.equals("image/jpeg") && format.equalsIgnoreCase("jpeg"))) throw new IllegalArgumentException("El formato no coincide con el contenido de la imagen");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) throw new IllegalArgumentException("La imagen admite hasta 4096 × 4096 píxeles");
                if (reader.read(0) == null) throw new IllegalArgumentException("No se pudo leer la imagen");
            } finally { reader.dispose(); }
        } catch (java.io.IOException e) { throw new IllegalArgumentException("El archivo no contiene una imagen válida"); }
    }
    @Override public byte[] contenido() { return Arrays.copyOf(contenido, contenido.length); }
    public String version() {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(contenido)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
