package cl.duoc.vidasalud.notifications.messaging.consumer.notificaciones;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.vidasalud.notifications.domain.NotificacionService;
import cl.duoc.vidasalud.notifications.messaging.ErrorRecuperable;
import cl.duoc.vidasalud.notifications.messaging.events.AtencionEvent;

/**
 * Consumidor del dominio de notificaciones: escucha q.notificaciones.email y
 * avisa al paciente por correo.
 *
 * Es el caso de uso que mejor justifica la mensajeria. Mandar un correo puede
 * tardar segundos y puede fallar por causas ajenas al sistema; no tiene por
 * que retrasar ni voltear la confirmacion de una atencion. Por eso el aviso
 * sale de una cola y no del hilo que atiende la peticion HTTP.
 *
 * ACK manual, con la misma politica del resto del sistema:
 *
 *   aviso enviado      -> basicAck
 *   mensaje invalido   -> basicNack sin requeue (a la DLQ)
 *   fallo transitorio  -> basicNack con requeue la primera vez, DLQ la segunda
 */
@Component
public class NotificacionEmailListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEmailListener.class);

    private final NotificacionService notificaciones;

    public NotificacionEmailListener(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @RabbitListener(queues = "${vidasalud.messaging.queues.notificaciones}")
    public void recibir(AtencionEvent evento,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean reentregado)
            throws IOException {

        boolean segundoIntento = Boolean.TRUE.equals(reentregado);

        try {
            notificaciones.enviar(evento.atencionId(), evento.patientEmail(),
                    evento.patientName(), evento.status(), evento.scheduledAt());
            channel.basicAck(deliveryTag, false);

        } catch (IllegalArgumentException e) {
            // Sin correo o con un estado que no genera aviso: reintentar no
            // cambia nada, el mensaje esta mal de origen.
            log.error("Aviso descartado a la DLQ. atencionId={} motivo={}",
                    evento.atencionId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);

        } catch (ErrorRecuperable e) {
            if (segundoIntento) {
                log.error("El servidor de correo sigue sin responder; atencion {} a la DLQ.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("El servidor de correo no responde; se reintenta el aviso de la atencion {}.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }

        } catch (Exception e) {
            if (segundoIntento) {
                log.error("Error inesperado repetido; aviso de la atencion {} a la DLQ.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.error("Error inesperado enviando el aviso de la atencion {}; un reintento.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    /**
     * Consumidor de la DLQ de notificaciones. Un paciente que no recibio su
     * aviso es un problema de atencion al cliente, no un dato desechable: se
     * registra con nivel error para que quede en el log del servicio.
     */
    @RabbitListener(queues = "${vidasalud.messaging.dlq.notificaciones}")
    public void recibirDeLaDlq(AtencionEvent evento,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.error("DLQ notificaciones: {} no recibio el aviso de su atencion {} ({}).",
                evento.patientEmail(), evento.atencionId(), evento.status());
        channel.basicAck(deliveryTag, false);
    }
}
