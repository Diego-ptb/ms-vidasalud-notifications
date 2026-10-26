package cl.duoc.vidasalud.notifications.domain;

import java.time.Instant;

/** Aviso enviado a un paciente. */
public record Notificacion(
        Long atencionId,
        String destinatario,
        String asunto,
        String cuerpo,
        Instant enviadoEn) {
}
