package ar.com.coto.stock.controller;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import java.lang.annotation.*;

/** Inyecta el nombre de usuario autenticado, que queda registrado como operador del movimiento. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(hidden = true)
@CurrentSecurityContext(expression = "authentication.name")
@interface OperadorAutenticado { }
