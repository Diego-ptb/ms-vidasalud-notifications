# ms-vidasalud-notifications

Microservicio de **notificaciones** del caso VidaSalud · DSY1107 Desarrollo Cloud
Native I.

Avisa al paciente cuando su atención se confirma o se cancela. Es un servicio
consumidor: no tiene endpoints de escritura y nadie le pide que envíe un aviso. Escucha
la cola `q.notificaciones.email` y actúa.

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
        │
        └─ publica "atencion.confirmada" en vidasalud.atenciones.topic
                 │
                 ├─→ q.admision.ticket        (lo consume appointments)
                 └─→ q.notificaciones.email   (lo consume este servicio)
```

La cola está ligada al exchange **topic** con dos routing keys,
`atencion.confirmada` y `atencion.cancelada`. Las dos generan un correo, con texto
distinto, y el productor no tuvo que saberlo: eso es lo que permite un topic frente a un
direct.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/notifications` | Avisos enviados, del más reciente al más antiguo |

Solo lectura a propósito: los avisos los crea el consumidor de la cola, no una petición
HTTP. Existe para poder demostrar la mensajería: se confirma una atención desde el
frontend y el aviso aparece aquí sin que nadie haya llamado a este servicio.

En el BFF, esta ruta está permitida a **ADMIN** y **RECEPCIONISTA**. Un paciente no tiene
por qué ver los avisos enviados a otros.

## Manejo de errores

ACK manual. El consumidor decide explícitamente qué hacer con cada mensaje:

| Situación | Decisión |
|---|---|
| Aviso enviado | `basicAck` |
| Mensaje sin correo del paciente | `basicNack` sin requeue → DLQ |
| Estado que no genera aviso | `basicNack` sin requeue → DLQ |
| Servidor de correo caído, 1ª vez | `basicNack` con requeue → se reintenta |
| Servidor de correo caído, 2ª vez | `basicNack` sin requeue → DLQ |

El límite de un reintento es deliberado: un mensaje que falla dos veces no se arregla
insistiendo, y dejarlo girando en la cola bloquea a los demás. Lo que llega a
`q.notificaciones.email.dlq` se registra con nivel `error`: un paciente que no recibió su
aviso es un problema de atención al cliente, no un dato desechable.

## El envío es simulado

No hay servidor de correo en el alcance de la asignatura. El envío se registra en el log
y se conserva en memoria (los últimos 50) para poder mostrarlo.

Lo que sí es real es el camino: el aviso nace de un mensaje de RabbitMQ y no de la llamada
HTTP del usuario. Sustituir `NotificacionService` por uno que hable con un SMTP es
cambiar una clase; el resto del sistema no se entera.

## Configuración

| Variable | Por defecto | Para qué |
|---|---|---|
| `RABBIT_ADDRESSES` | — | `rabbit1:5672,rabbit2:5672`, las dos del clúster |
| `RABBIT_USER` / `RABBIT_PASS` | `admin` / `admin` | Credenciales del broker |
| `INTERNAL_KEY` | `vidasalud-internal` | Clave compartida con el BFF |

Los nombres de colas, exchanges y routing keys están centralizados en el bloque
`vidasalud.messaging` de `application.yml` y se leen con `MessagingProperties`. Ninguna
clase escribe un nombre de cola.

## Ejecutar

```bash
mvn spring-boot:run        # http://localhost:8091
mvn test                   # 11 pruebas, sin broker ni red
```
