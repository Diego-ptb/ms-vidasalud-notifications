package cl.duoc.vidasalud.notifications.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Redaccion y envio de los avisos al paciente.
 *
 * No hay servidor de correo en el alcance de la asignatura, asi que el envio se
 * registra en el log y se conserva en memoria para poder mostrarlo. Lo que si
 * es real es el camino: el aviso nace de un mensaje de RabbitMQ y no de la
 * llamada HTTP del usuario, que es justamente el desacople que se busca.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd-MM-yyyy 'a las' HH:mm").withZone(ZoneId.of("America/Santiago"));

    /** Ultimos avisos enviados, para la demostracion. Tope para no crecer sin fin. */
    private static final int MAX_HISTORIAL = 50;

    private final Deque<Notificacion> historial = new ArrayDeque<>();

    /**
     * Envia el aviso correspondiente al estado de la atencion.
     *
     * @throws IllegalArgumentException si el mensaje no trae destinatario o
     *         trae un estado que no genera aviso. Son errores definitivos: el
     *         consumidor los manda a la DLQ en vez de reintentarlos.
     */
    public synchronized Notificacion enviar(Long atencionId, String email, String paciente,
                                            String estado, Instant fechaAtencion) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "La atencion " + atencionId + " no trae correo del paciente: no hay a quien avisar.");
        }

        String asunto;
        String cuerpo;
        String cuando = fechaAtencion == null ? "la fecha agendada" : FORMATO.format(fechaAtencion);

        switch (estado == null ? "" : estado) {
            case "CONFIRMADA" -> {
                asunto = "VidaSalud: su atencion quedo confirmada";
                cuerpo = "Hola " + nombreDe(paciente) + ", su atencion fue confirmada para el "
                        + cuando + ". Presentese 15 minutos antes en el centro.";
            }
            case "CANCELADA" -> {
                asunto = "VidaSalud: su atencion fue cancelada";
                cuerpo = "Hola " + nombreDe(paciente) + ", su atencion del " + cuando
                        + " fue cancelada. Puede agendar una nueva hora cuando quiera.";
            }
            default -> throw new IllegalArgumentException(
                    "El estado " + estado + " de la atencion " + atencionId + " no genera aviso.");
        }

        Notificacion aviso = new Notificacion(atencionId, email, asunto, cuerpo, Instant.now());
        registrar(aviso);

        log.info("Aviso enviado a {} por la atencion {}: {}", email, atencionId, asunto);
        return aviso;
    }

    /** Avisos enviados, del mas reciente al mas antiguo. */
    public synchronized List<Notificacion> enviados() {
        return List.copyOf(historial);
    }

    private void registrar(Notificacion aviso) {
        historial.addFirst(aviso);
        while (historial.size() > MAX_HISTORIAL) {
            historial.removeLast();
        }
    }

    private String nombreDe(String paciente) {
        if (paciente == null || paciente.isBlank()) {
            return "paciente";
        }
        return paciente.split(" ")[0];
    }
}
