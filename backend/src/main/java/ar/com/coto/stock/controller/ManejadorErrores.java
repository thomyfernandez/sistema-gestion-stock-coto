package ar.com.coto.stock.controller;

import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.model.StockInsuficienteException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.net.URI;
import java.time.Clock;
import java.util.*;

/** Errores RFC 9457; el cliente nunca recibe SQL, stack traces ni nombres de proveedores internos. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {
    private final Clock reloj;
    public ManejadorErrores(Clock reloj) { this.reloj = reloj; }

    @ExceptionHandler(EntidadNoEncontradaException.class)
    public ResponseEntity<Object> noEncontrado(EntidadNoEncontradaException ex, WebRequest request) {
        return responder(ex, HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", ex.getMessage(), request);
    }
    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<Object> sinStock(StockInsuficienteException ex, WebRequest request) {
        return responder(ex, HttpStatus.CONFLICT, "STOCK_INSUFICIENTE", ex.getMessage(), request);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> argumento(IllegalArgumentException ex, WebRequest request) {
        return responder(ex, HttpStatus.BAD_REQUEST, "REGLA_INVALIDA", ex.getMessage(), request);
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> estado(IllegalStateException ex, WebRequest request) {
        return responder(ex, HttpStatus.CONFLICT, "ESTADO_INCOMPATIBLE", ex.getMessage(), request);
    }
    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<Object> desborde(ArithmeticException ex, WebRequest request) {
        return responder(ex, HttpStatus.BAD_REQUEST, "CANTIDAD_FUERA_RANGO", "La cantidad o el cálculo excede el rango admitido", request);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> integridad(DataIntegrityViolationException ex, WebRequest request) {
        return responder(ex, HttpStatus.CONFLICT, "CONFLICTO_DATOS", "La operación entra en conflicto con datos existentes o restricciones de integridad", request);
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> concurrencia(ConcurrencyFailureException ex, WebRequest request) {
        var problema = problema(HttpStatus.SERVICE_UNAVAILABLE, "INVENTARIO_OCUPADO", "El inventario está ocupado; reintentá la operación");
        var headers = new HttpHeaders(); headers.set(HttpHeaders.RETRY_AFTER, "1");
        return handleExceptionInternal(ex, problema, headers, HttpStatus.SERVICE_UNAVAILABLE, request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> inesperado(Exception ex, WebRequest request) {
        logger.error("Error inesperado al procesar la solicitud", ex);
        return responder(ex, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar la operación", request);
    }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.computeIfAbsent(e.getField(), k -> new ArrayList<>())
                .add(Objects.requireNonNullElse(e.getDefaultMessage(), "Valor inválido")));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errores.computeIfAbsent("solicitud", k -> new ArrayList<>())
                .add(Objects.requireNonNullElse(e.getDefaultMessage(), "Solicitud inválida")));
        var problema = problema(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA", "Revisá los campos de la solicitud");
        problema.setProperty("errores", errores);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }
    @Override protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problema = problema(status, "PARAMETRO_INVALIDO", "Los parámetros de ruta o consulta no cumplen las restricciones");
        Map<String, List<String>> errores = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            String nombre = Objects.requireNonNullElse(r.getMethodParameter().getParameterName(), "parametro");
            errores.put(nombre, r.getResolvableErrors().stream().map(e -> Objects.requireNonNullElse(e.getDefaultMessage(), "Valor inválido")).toList());
        });
        problema.setProperty("errores", errores);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }
    @Override protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problema(status, "JSON_INVALIDO",
                "El cuerpo JSON es inválido: revisá los nombres de campos, tipos, valores y campos obligatorios"), headers, status, request);
    }
    @Override protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problema(status, "PARAMETRO_INVALIDO", "El tipo o valor de un parámetro es inválido"), headers, status, request);
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = body instanceof ProblemDetail p ? p : problema(status, "HTTP_" + status.value(), detalle(status.value()));
        if (problema.getProperties() == null || !problema.getProperties().containsKey("codigo")) {
            problema.setProperty("codigo", "HTTP_" + status.value());
            problema.setDetail(detalle(status.value()));
        }
        problema.setProperty("timestamp", reloj.instant());
        if (request instanceof ServletWebRequest servlet) problema.setInstance(URI.create(servlet.getRequest().getRequestURI()));
        return super.handleExceptionInternal(ex, problema, headers, status, request);
    }
    private ResponseEntity<Object> responder(Exception ex, HttpStatus status, String codigo, String detalle, WebRequest request) {
        return handleExceptionInternal(ex, problema(status, codigo, detalle), new HttpHeaders(), status, request);
    }
    private ProblemDetail problema(HttpStatusCode status, String codigo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalle);
        p.setProperty("codigo", codigo);
        return p;
    }
    private String detalle(int status) {
        return switch (status) {
            case 404 -> "No se encontró el recurso solicitado";
            case 405 -> "El método HTTP no está permitido para este recurso";
            case 406 -> "El formato de respuesta solicitado no está disponible";
            case 415 -> "Usá Content-Type: application/json";
            default -> "No se pudo procesar la solicitud";
        };
    }
}
