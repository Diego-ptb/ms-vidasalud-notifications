package cl.duoc.vidasalud.notifications.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import cl.duoc.vidasalud.notifications.domain.Notificacion;
import cl.duoc.vidasalud.notifications.domain.NotificacionService;

/**
 * Avisos a los pacientes.
 *
 * Este servicio no conoce RabbitMQ. El envio lo dispara
 * ms-vidasalud-rabbit-admin, que es quien consume la cola
 * q.notificaciones.email y llama a POST /api/notifications con la clave
 * interna. Aqui solo vive la redaccion y el envio del aviso.
 *
 * El GET existe para poder demostrar que la mensajeria funciona: se confirma
 * una atencion en el frontend y el aviso aparece aqui sin que ningun usuario
 * haya llamado a este servicio.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    /**
     * Peticion de envio. La manda el consumidor de la cola, no un usuario.
     *
     * Se valida el id porque un aviso sin atencion asociada no se puede
     * auditar despues.
     */
    public record AvisoRequest(
            @NotNull(message = "El id de la atencion es obligatorio.")
            Long atencionId,
            String email,
            String paciente,
            String estado,
            Instant fechaAtencion) {
    }

    @GetMapping
    public List<Notificacion> enviados() {
        return notificaciones.enviados();
    }

    /**
     * Envia el aviso correspondiente al estado de la atencion.
     *
     * Responde 400 cuando el mensaje no trae correo o trae un estado que no
     * genera aviso. Esa distincion importa: el consumidor interpreta el 4xx
     * como error definitivo y manda el mensaje a la DLQ en vez de reintentarlo.
     */
    @PostMapping
    public ResponseEntity<Notificacion> enviar(@Valid @RequestBody AvisoRequest request) {
        try {
            Notificacion aviso = notificaciones.enviar(
                    request.atencionId(), request.email(), request.paciente(),
                    request.estado(), request.fechaAtencion());

            return ResponseEntity.status(HttpStatus.CREATED).body(aviso);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
