# Integración WhatsApp (WAHA) — envío de tickets al DM

Cada compra confirmada (Orden + Boletos + Pago) genera un mensaje de WhatsApp con el resumen del
ticket, armado **leyendo la DB** (Orden, Pago, MetodoPago, Boleto, Función, Establecimiento, Zona/Asiento).

## 1. Levantar WAHA

```bash
docker run -d --name waha -p 3000:3000 \
  -e WAHA_API_KEY=UNA_CLAVE_LARGA_ALEATORIA \
  -v waha_sessions:/app/.sessions \
  devlikeapro/waha
```
1. Abre `http://localhost:3000/dashboard`, inicia la sesión `default` y escanea el QR con WhatsApp.
2. Verifica: `GET /api/sessions/default` → `"status": "WORKING"`.
3. En producción ponlo detrás de un proxy HTTPS (Caddy/Nginx/Traefik). La app **rechaza http://** en release.

## 2. Configurar la app

Copia `local.properties.example` → `local.properties` (no se versiona) y completa
`waha.baseUrl`, `waha.apiKey`, `waha.session`, `waha.chatDestino`. También funcionan variables de
entorno (`WAHA_BASEURL`, `WAHA_APIKEY`, `WAHA_SESSION`, `WAHA_CHATDESTINO`) para CI.
Si faltan URL o destino la integración queda **desactivada** y la app funciona igual.

Emulador Android → WAHA en tu PC: `waha.baseUrl=http://10.0.2.2:3000` (solo builds *debug*).

## 3. Cómo funciona (outbox transaccional)

```
PaymentActivity → CompraRepository.realizarCompra
   └─ UNA transacción Room: Orden + Boletos + Pago + fila WhatsappOutbox(PENDIENTE)
        └─ tras el commit: WorkManager (requiere red)
             └─ WahaSyncWorker → OutboxProcessor
                  ├─ reclama la fila (atómico, con lease)
                  ├─ lee Orden/Pago/Boletos de la DB → MensajeTicketFormatter
                  └─ WahaClient: POST /api/sendText  (X-Api-Key)
```

| Garantía | Mecanismo |
|---|---|
| Sin compra sin notificación (ni al revés) | La fila de la cola se inserta en la misma transacción que la compra |
| Una sola notificación por orden | `UNIQUE(ID_Orden)` + `INSERT … IGNORE` |
| La compra nunca falla por WhatsApp | El envío es asíncrono; errores al programar se ignoran (hay respaldo periódico) |
| No se pierden mensajes | La cola vive en SQLite; WorkManager sobrevive a cierres/reinicios; revisión cada 15 min y al abrir la app |
| Recuperación tras crash | Estado `ENVIANDO` con *lease* de 2 min: si el proceso muere, otra pasada la retoma |
| Sin carreras entre workers | `UPDATE` condicionado (claim atómico) + trabajo único de WorkManager + candado |
| Datos fieles | El texto se reconstruye desde la DB al enviar; no se duplica información en la cola |
| Migración sin pérdida | `Migration(2,3)` solo añade la tabla (se eliminó el `fallbackToDestructiveMigration` para v2→v3) |

### Clasificación de errores

| Respuesta | Acción |
|---|---|
| 2xx | `ENVIADO` (se guarda el id de mensaje de WAHA) |
| Timeout / red / 5xx / 408 / 429 | Reintento con backoff exponencial + jitter (30 s → 1 h), respeta `Retry-After`; cuenta como intento. Tras **10** intentos → `FALLIDO` |
| 401 / 403 / sesión de WhatsApp no `WORKING` / TLS / redirección | Reintento ≥ 5 min **sin consumir intentos** (no es culpa del mensaje; una caída larga no agota la cola) |
| 400 y demás 4xx | `FALLIDO` inmediato (el mensaje/destino es inválido) |
| 5 fallos de infraestructura seguidos | *Circuit breaker*: pausa 60 s y luego una petición de prueba |

Reabrir las `FALLIDO` tras corregir la configuración: `WahaSyncScheduler.reintentarFallidas(context)`.
Inspección: tabla `WhatsappOutbox` (`Estado`, `Intentos`, `UltimoError`, `MensajeIdWaha`).

## 4. Seguridad

- HTTPS obligatorio en release (`network_security_config.xml` + validación en el cliente); cleartext solo en debug.
- No se siguen redirecciones (la API key no se reenvía a otro host); respuesta acotada a 64 KB; timeouts 10 s / 20 s.
- La API key y el texto de los mensajes **nunca** se escriben en logs ni en `UltimoError`.
- El mensaje solo lleva datos que ya guarda la app: últimos 4 dígitos de la tarjeta (nunca número/CVV) y correo PayPal **enmascarado**.
- Texto libre (nombre de evento, etc.) se sanea para que no rompa el formato de WhatsApp.
- Recomendado: fijar el certificado del servidor con `<pin-set>` (plantilla comentada en `network_security_config.xml`).

## 5. Limitaciones conocidas (leer)

1. **API key dentro del APK.** Cualquier clave embebida en una app móvil puede extraerse. Es aceptable para
   un entorno académico/demo con una WAHA propia, pero para producción lo correcto es que la app hable con
   **tu propio backend** (que guarda la clave de WAHA) y este llame a WAHA. El código está aislado en
   `WahaClient`/`WahaConfigProvider`, por lo que cambiar el destino es sencillo.
2. **Entrega "al menos una vez".** WAHA no ofrece clave de idempotencia: si WAHA acepta el mensaje pero la
   respuesta se pierde (timeout), el reintento puede duplicarlo. El mensaje incluye la referencia de la
   orden para reconocerlo. Se prefiere un duplicado raro a perder un comprobante.
3. **Destino único.** La tabla `Usuario` no tiene teléfono, así que los tickets van a un DM fijo
   (`waha.chatDestino`). Para enviarlos al cliente habría que añadir `Telefono` a `Usuario` (con su
   validación en el registro) y pasar el chatId por orden.
4. Las compras anteriores a esta versión no se notifican (no hay *backfill*).
5. La reconstrucción del ticket exige un pago `Aprobado`; si no lo hay, la fila queda `FALLIDO`.

## 6. Pruebas

`app/src/test/java/.../waha/` (JUnit 4, sin Android): cliente contra un WAHA simulado (éxito, 5xx, 429,
401, 400, 422 con sesión sin QR, timeout, servidor caído, HTTP bloqueado, sin redirecciones), backoff,
circuit breaker, formato del mensaje, normalización del chatId, procesador de la cola (reintentos,
agotamiento, circuito, recuperación tras crash, lease vigente) y un caso extremo a extremo
"WAHA cae y se recupera".
