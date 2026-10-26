package cl.duoc.vidasalud.notifications.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.vidasalud.notifications.domain.Notificacion;
import cl.duoc.vidasalud.notifications.domain.NotificacionService;

/**
 * Avisos ya enviados. Existe para poder demostrar que la mensajeria funciona:
 * se confirma una atencion por el frontend y los avisos aparecen aqui sin que
 * nadie haya llamado a este servicio.
 *
 * Es solo lectura a proposito: los avisos los crea el consumidor de la cola,
 * no una peticion HTTP.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public List<Notificacion> enviados() {
        return notificaciones.enviados();
    }
}
