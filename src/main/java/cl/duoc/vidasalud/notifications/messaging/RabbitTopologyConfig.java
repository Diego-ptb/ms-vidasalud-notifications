package cl.duoc.vidasalud.notifications.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ del dominio de notificaciones.
 *
 * La cola q.notificaciones.email se liga al exchange topic con DOS routing
 * keys: la confirmacion y la cancelacion de una atencion. Las dos generan un
 * correo al paciente, con texto distinto, y el productor no tuvo que saberlo:
 * eso es lo que permite un exchange topic frente a uno direct.
 */
@Configuration
public class RabbitTopologyConfig {

    private final MessagingProperties nombres;

    public RabbitTopologyConfig(MessagingProperties nombres) {
        this.nombres = nombres;
    }

    // ------------------------------------------------------------- exchanges

    @Bean
    TopicExchange exchangeAtenciones() {
        return new TopicExchange(nombres.getExchanges().getTopic(), true, false);
    }

    @Bean
    DirectExchange exchangeComandos() {
        return new DirectExchange(nombres.getExchanges().getDirect(), true, false);
    }

    @Bean
    DirectExchange exchangeDlx() {
        return new DirectExchange(nombres.getExchanges().getDlx(), true, false);
    }

    // -------------------------------------------------- cola de notificaciones

    @Bean
    Queue colaNotificaciones() {
        return QueueBuilder.durable(nombres.getQueues().getNotificaciones())
                .withArguments(Map.of(
                        "x-dead-letter-exchange", nombres.getExchanges().getDlx(),
                        "x-dead-letter-routing-key", nombres.getDlq().getNotificaciones()))
                .build();
    }

    @Bean
    Queue colaNotificacionesDlq() {
        return QueueBuilder.durable(nombres.getDlq().getNotificaciones()).build();
    }

    /** Aviso de atencion confirmada. */
    @Bean
    Binding bindingNotificacionConfirmada() {
        return BindingBuilder.bind(colaNotificaciones())
                .to(exchangeAtenciones())
                .with(nombres.getRouting().getAtencionConfirmada());
    }

    /** Aviso de atencion cancelada: misma cola, otra routing key. */
    @Bean
    Binding bindingNotificacionCancelada() {
        return BindingBuilder.bind(colaNotificaciones())
                .to(exchangeAtenciones())
                .with(nombres.getRouting().getAtencionCancelada());
    }

    @Bean
    Binding bindingNotificacionesDlq() {
        return BindingBuilder.bind(colaNotificacionesDlq())
                .to(exchangeDlx())
                .with(nombres.getDlq().getNotificaciones());
    }
}
