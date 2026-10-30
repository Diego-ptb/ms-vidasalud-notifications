# ms-vidasalud-notifications

Microservicio de **notificaciones** del caso VidaSalud · DSY1107 Desarrollo Cloud
Native I.

Avisa al paciente cuando su atención se confirma o se cancela.

Este servicio **no habla AMQP**: su `pom.xml` no incluye el starter de RabbitMQ y no
contiene ninguna clase de mensajería. El envío lo dispara `ms-vidasalud-rabbit-admin`,
que consume la cola `q.notificaciones.email` y llama por HTTP a este servicio. Aquí solo
vive la redacción y el envío del aviso.

## Por qué es un servicio aparte

Mandar un correo puede tardar segundos y puede fallar por causas ajenas al sistema —el
servidor de correo no responde, la casilla rebota—. Nada de eso debería retrasar ni
voltear la confirmación de una atención.

Antes de la mensajería, la única forma de avisar era hacerlo dentro de la misma llamada
HTTP que confirmaba la atención: el paciente esperaba el correo para ver su confirmación,
y un fallo del correo rompía la operación. Ahora el aviso sale de una cola, y el servicio
que confirma la atención ni sabe que existe.

## Flujo

```
appointments confirma la atención
        │  (HTTP)
        └─→ ms-vidasalud-rabbit-admin publica "atencion.confirmada"
                 │
                 ├─→ q.admision.ticket        → PUT  {appointments}/.../ticket
                 └─→ q.notificaciones.email   → POST {notifications}/api/notifications
```

La cola está ligada al exchange **topic** con dos routing keys,
`atencion.confirmada` y `atencion.cancelada`. Las dos generan un correo, con texto
distinto, y el productor no tuvo que saberlo.

## Endpoints

| Método | Ruta | Quién llama |
|---|---|---|
| `GET` | `/api/notifications` | El personal, a través del BFF |
| `POST` | `/api/notifications` | ms-vidasalud-rabbit-admin, al consumir la cola |

El `GET` existe para poder demostrar la mensajería: se confirma una atención desde el
frontend y el aviso aparece aquí sin que ningún usuario haya llamado a este servicio. En
el BFF está permitido a **ADMIN** y **RECEPCIONISTA**; un paciente no tiene por qué ver
los avisos enviados a otros.

El `POST` es interno y lo dispara el consumidor de la cola. Responde **400** cuando el
mensaje no trae correo o trae un estado que no genera aviso: el consumidor interpreta ese
4xx como error definitivo y manda el mensaje a la DLQ en vez de reintentarlo.

## Manejo de errores

El ACK manual vive en el servicio de mensajería, que es quien consume la cola. Lo que
aporta este servicio es la **distinción entre errores**, con los códigos HTTP que
devuelve:

| Situación | Respuesta | Qué hace el consumidor |
|---|---|---|
| Aviso enviado | `201` | `basicAck` |
| Sin correo del paciente | `400` | `basicNack` sin requeue → DLQ |
| Estado que no genera aviso | `400` | `basicNack` sin requeue → DLQ |
| Este servicio caído | sin respuesta | reintenta una vez, luego DLQ |

## El envío es simulado

No hay servidor de correo en el alcance de la asignatura. El envío se registra en el log
y se conserva en memoria (los últimos 50) para poder mostrarlo.

Lo que sí es real es el camino: el aviso nace de un mensaje de RabbitMQ y no de la llamada
HTTP del usuario. Sustituir `NotificacionService` por uno que hable con un SMTP es
cambiar una clase; el resto del sistema no se entera.

## Configuración

| Variable | Por defecto | Para qué |
|---|---|---|
| `INTERNAL_KEY` | `vidasalud-internal` | Clave compartida con el BFF y la mensajería |

No hay ninguna variable de RabbitMQ: este servicio no se conecta al broker.

## Ejecutar

```bash
mvn spring-boot:run        # http://localhost:8091
mvn test                   # 6 pruebas, sin dependencias externas
```
