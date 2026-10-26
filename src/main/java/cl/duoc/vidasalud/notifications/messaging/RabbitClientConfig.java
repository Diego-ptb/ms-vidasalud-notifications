package cl.duoc.vidasalud.notifications.messaging;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serializacion de los mensajes. Este servicio solo consume, asi que le basta
 * con usar el mismo converter que el productor.
 */
@Configuration
public class RabbitClientConfig {

    /**
     * JSON en vez de serializacion Java: el mensaje queda legible en la
     * Management UI y los consumidores no necesitan las mismas clases.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
