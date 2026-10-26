package cl.duoc.vidasalud.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificacionServiceTest {

    private NotificacionService service;

    @BeforeEach
    void setUp() {
        service = new NotificacionService();
    }

    private Instant manana() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("La confirmacion genera un aviso dirigido al paciente")
    void avisaLaConfirmacion() {
        Notificacion aviso = service.enviar(7L, "ana@example.cl", "Ana Perez", "CONFIRMADA", manana());

        assertThat(aviso.destinatario()).isEqualTo("ana@example.cl");
        assertThat(aviso.asunto()).contains("confirmada");
        assertThat(aviso.cuerpo()).startsWith("Hola Ana,");
    }

    @Test
    @DisplayName("La cancelacion genera otro texto, no el mismo")
    void avisaLaCancelacion() {
        Notificacion aviso = service.enviar(7L, "ana@example.cl", "Ana Perez", "CANCELADA", manana());

        assertThat(aviso.asunto()).contains("cancelada");
        assertThat(aviso.cuerpo()).contains("agendar una nueva hora");
    }

    @Test
    @DisplayName("Sin correo no hay a quien avisar: error definitivo")
    void fallaSinDestinatario() {
        assertThatThrownBy(() -> service.enviar(7L, "  ", "Ana Perez", "CONFIRMADA", manana()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no trae correo");
    }

    @Test
    @DisplayName("Un estado que no genera aviso se rechaza")
    void rechazaEstadosSinAviso() {
        // La cola esta ligada a dos routing keys; cualquier otro estado que
        // llegue es un error de topologia y debe quedar visible.
        assertThatThrownBy(() -> service.enviar(7L, "ana@example.cl", "Ana", "EN_ESPERA", manana()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no genera aviso");
    }

    @Test
    @DisplayName("Sin nombre del paciente el saludo sigue siendo correcto")
    void saludaSinNombre() {
        Notificacion aviso = service.enviar(7L, "ana@example.cl", null, "CONFIRMADA", manana());

        assertThat(aviso.cuerpo()).startsWith("Hola paciente,");
    }

    @Test
    @DisplayName("El historial queda del mas reciente al mas antiguo")
    void guardaElHistorialEnOrden() {
        service.enviar(1L, "uno@example.cl", "Uno", "CONFIRMADA", manana());
        service.enviar(2L, "dos@example.cl", "Dos", "CONFIRMADA", manana());

        assertThat(service.enviados()).hasSize(2);
        assertThat(service.enviados().get(0).atencionId()).isEqualTo(2L);
    }
}
