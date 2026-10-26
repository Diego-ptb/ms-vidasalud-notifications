package cl.duoc.vidasalud.notifications.messaging.consumer.notificaciones;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;

import cl.duoc.vidasalud.notifications.domain.Notificacion;
import cl.duoc.vidasalud.notifications.domain.NotificacionService;
import cl.duoc.vidasalud.notifications.messaging.ErrorRecuperable;
import cl.duoc.vidasalud.notifications.messaging.events.AtencionEvent;

@ExtendWith(MockitoExtension.class)
class NotificacionEmailListenerTest {

    private static final long TAG = 5L;

    @Mock
    private NotificacionService notificaciones;

    @Mock
    private Channel channel;

    private NotificacionEmailListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificacionEmailListener(notificaciones);
    }

    private AtencionEvent evento() {
        return new AtencionEvent(7L, "Ana Perez", "ana@example.cl", 1L,
                Instant.now(), "CONFIRMADA", "BOX-01", Instant.now());
    }

    @Test
    @DisplayName("Aviso enviado: ACK")
    void confirmaCuandoEnviaElAviso() throws IOException {
        when(notificaciones.enviar(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new Notificacion(7L, "ana@example.cl", "asunto", "cuerpo", Instant.now()));

        listener.recibir(evento(), channel, TAG, false);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Mensaje sin correo: NACK sin requeue, a la DLQ")
    void mandaALaDlqLoQueNoTieneDestinatario() throws IOException {
        when(notificaciones.enviar(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("no trae correo del paciente"));

        listener.recibir(evento(), channel, TAG, false);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("Servidor de correo caido: se reintenta una vez")
    void reintentaSiElCorreoNoResponde() throws IOException {
        when(notificaciones.enviar(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new ErrorRecuperable("smtp caido", null));

        listener.recibir(evento(), channel, TAG, false);

        verify(channel).basicNack(TAG, false, true);
    }

    @Test
    @DisplayName("Servidor de correo que sigue caido: a la DLQ")
    void descartaTrasElSegundoIntento() throws IOException {
        when(notificaciones.enviar(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new ErrorRecuperable("smtp caido", null));

        listener.recibir(evento(), channel, TAG, true);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("La DLQ se consume dejando rastro del aviso perdido")
    void consumeLaDlq() throws IOException {
        listener.recibirDeLaDlq(evento(), channel, TAG);

        verify(channel).basicAck(TAG, false);
    }
}
