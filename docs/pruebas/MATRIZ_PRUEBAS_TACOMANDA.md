# Matriz de pruebas — Tacomanda

Corrida E2E sobre **2 emuladores** (cocina + mesera) · build `1.0.1 (2)` · md5 `7b9eb36856a2c5b1df9e77360d3cdc46` · 2026-11-21

## Resumen

- Casos en la matriz: **190** (184 del catálogo + 6 derivados de las mejoras UX: menú rápido y QR de conexión)
- Suite JVM: **45/45** PASA
- Protocolo TCP real: **51/51** PASA
- Cobertura de la matriz: **162 PASA / 0 FALLA / 0 observación / 8 bloqueados / 20 no ejecutados**

## Estados

| Estado | Casos | % |
|---|---|---|
| PASA | 162 | 85.3% |
| FALLA | 0 | 0.0% |
| OBSERVACION | 0 | 0.0% |
| BLOQUEADO | 8 | 4.2% |
| NO EJECUTADO | 20 | 10.5% |

## Hallazgos y cambios

- **La APK instalada en los emuladores era anterior al renombrado a Tacomanda (label y firma viejas); versionName/Code no distinguían el build.** → Se reinstaló el APK HEAD y se re-corrió toda la fase E2E.
- **El gesto → solo cobraba: no permitía entregar la comida antes de pagar (caso real: comen primero y pagan al final).** → Menú rápido implementado: deslizar → abre «Entregar» y «Cobrar/Devolver». Requiere recompilar e instalar.
- **El teléfono de mesera creaba files/taqueria.json con el menú semilla al arrancar (contradecía «la mesera no guarda datos de negocio»): applyRole(WAITER) tocaba app.server.stop() e inicializaba el OrderStore perezoso.** → Corregido: solo se inicia/detiene el servidor si realmente estaba arrancado; la mesera ya no crea taqueria.json.
- **Rotar durante la captura pierde la pantalla de captura: `page`/`step` usan `remember` (no saveable); el borrador queda inaccesible y «+ Nueva orden» lo limpia.** → Documentado como comportamiento actual (UI-ROTACION-01). Queda como mejora pendiente.
- **Cobertura JVM sin pruebas para: protocolo TCP/JSON, WaiterDraft (split/toggleMod/retainAvailable/differsFrom), parseMoney/money/elapsedText, AppState.payStatus/isClosed y licencia de red.** → Documentado con archivo:línea en evidence/unitarios/AUDITORIA.md (25 huecos).
- **No había forma cómoda de conectar la mesera: mDNS falla en Android 17 o hay que escribir la IP a mano.** → Implementado: cocina muestra QR de conexión con 📲 y la mesera lo escanea desde ⚙ → Conexión; parser con 11 pruebas unitarias nuevas.
- **Casos no ejecutables en AVD: escaneo de QR por cámara, mDNS entre emuladores, build release sin servidor, TalkBack.** → Marcados como BLOQUEADO en la matriz con su justificación.

## Archivos

- `MATRIZ_PRUEBAS_TACOMANDA.xlsx` — matriz completa (Resumen, Matriz de casos, Ejecución E2E, Protocolo TCP, Unitarias, Hallazgos, Evidencias)
- `evidence/e2e/capturas/` — capturas de pantalla de la corrida
- `evidence/e2e/{protocolo,licencia,sistema,red_ui,resultados}.json` — resultados crudos
- `evidence/unitarios/AUDITORIA.md` — auditoría de la suite JVM y huecos