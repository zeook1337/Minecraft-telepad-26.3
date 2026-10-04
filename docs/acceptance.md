# Registro de aceptación

Aceptación inicial: 2 de octubre de 2026 (America/Santiago).
La aceptación de `26.3-7.1.0-dev` está al final, en «Color crafting acceptance».

Este documento resume la aceptación histórica del port. Las evidencias crudas de
`docs/evidence/`, los mundos y las instancias locales se excluyen de Git porque
pueden contener rutas del equipo y datos de jugadores. Sus referencias de abajo
describen archivos locales, no descargas disponibles en el repositorio público.
La compilación y JUnit se pueden repetir con el wrapper del proyecto; los scripts
de integración históricos requieren preparar las instancias externas indicadas.

## Entorno y artefacto inicial — 26.3-7.0.0-dev

Minecraft Java 26.3, Forge/MDK 26.3-66.0.9, Temurin 25.0.4.1+1 Windows x64,
Gradle 9.7.1 y ForgeGradle 7.0.17. Los checksums oficiales del MDK y JDK fueron
comprobados antes de extraer. `gradlew.bat --version` confirma Java 25 tanto en
Launcher como en Daemon. La selección no cambia el Java global del equipo.

Artefacto: `build/libs/telepads-26.3-7.0.0-dev.jar`. Es una adaptación nueva sin
importación automática de 1.19.2. Contiene licencia GPL v3, atribución, licencia
del MDK y metadatos acotados a Minecraft 26.3 / Forge 66.

## Pruebas y resultados

| Comprobación | Resultado y cobertura |
| --- | --- |
| `gradlew.bat clean build` con Java 25 | Compilación limpia, JAR sin hooks de prueba y diez pruebas JUnit correctas |
| `-PtelepadsGameTests runGameTestServer` | Seis GameTests requeridos de `telepads:*`, todos correctos |
| Cliente gráfico optativo | Nombre, nueve amigos, tecla punto, estados, lista desplazable, páginas y lista vacía |
| Dos clientes TCP con JAR empacado | Amigos, formulario/compartición, acceso separado, activación, viaje, repetición/invención, Nether, chunk descargado, ausente y portable |
| Reinicio del servidor con ambos clientes | Catálogo, amigos, usuarios compartidos, ausencia, identidad, colores y mejoras conservados |
| Servidor con JAR limpio | Arranque dedicado, bloque guardado/recuperado y cierre correcto, sin dependencia de clases de cliente |
| Referencia 1.19.2 | 100 archivos SHA-256 comparados, cero diferencias |

JUnit cubre codec versionado del catálogo, índices/duplicados, permisos, amigos,
olvido individual, aislamiento por mundo, colocación/token, espera de 60 ticks,
cambio/cancelación/caducidad, saldo de XP y prioridad de niveles, y parser con
intervalos negativos e inclusivos. El informe está en `evidence/junit/`.

Los GameTests ejecutan APIs reales del servidor, con conexiones embebidas para
jugadores: plataforma de 0.2 bloques y datos, nombres/contextos, descubrimiento y
compartición, peticiones inventadas/repetidas/acceso revocado, eventos de logout y
muerte, cancelación de Forge, coste exacto en niveles y puntos, saldo insuficiente,
llegada bloqueada/sin apoyo/fuera del borde, ausencia y dimensión inexistente,
transmisor solo en origen, redstone, herramientas públicas, tintes y devolución,
drops únicos y componentes al recolocar, bead/collar con candidatos inválidos y
lleno de inventario, las cuatro recetas, menú real de yunque con totales 2–8 y
consumo completo, opción deshabilitada/9 perlas, dragón vivo, destinos fijos e
intervalos en el Nether, y retorno al modo normal al quitar configuración.
Informe canónico local: `evidence/gametest-report.xml`.

## Sesión con clientes gráficos y paquete

El servidor instalado en `../.tools/integration-server/` usa localhost:25577.
Dos procesos independientes Forge, `TelepadAlice` y `TelepadBob`, cargan
`telepads-acceptance.jar` empacado, con las mismas clases y recursos de producción
que la entrega. Los hooks optativos están solo en esa variante de aceptación.
La comparación por hash con el JAR limpio está en `evidence/artifact-check.json`.
Se compararon 80 archivos de código y recursos de producción, sin diferencias.
El JAR limpio de esa sesión pesa 218080 bytes y su SHA-256 es
`C0615427963393CA71E2CDADBBD6355CEAFBEA4E2040360468948313FBC039B1`.
Para repetir las comprobaciones del paquete, informes y referencia conservados,
ejecutar `./scripts/verify-artifact.ps1` desde `Telepad26.3/`.

Ambos clientes envían los paquetes normales del mod y usan sus controles reales.
Alice añade a Bob y confirma el formulario con compartir. Bob mantiene una lista
vacía y no recibe `AliceSecret`. Cada primer viaje cobra exactamente dos niveles;
una solicitud inventada y la repetición no añaden cobros. Alice viaja al Nether,
vuelve a un destino cuyo chunk fue comprobado descargado antes del recorrido y
confirma la ubicación de una plataforma ausente. El collar lleva al destino válido
más cercano; la bead usa un candidato autorizado de la dimensión actual. Ambos
consumen una unidad; el collar devuelve 1–2 hilos y ambos conservan el XP.

Marcadores observados: `INTEGRATION_UNLOADED_PASS`, `INTEGRATION_SHARED_PASS`,
`INTEGRATION_TRAVEL_PASS`, `INTEGRATION_DIMENSION_PASS`, `INTEGRATION_FAR_PASS`,
`INTEGRATION_NECKLACE_PASS`, `INTEGRATION_BEAD_PASS`, `INTEGRATION_SERVER_PASS` y,
tras reiniciar, `INTEGRATION_RESTART_PASS`.
Logs: `evidence/integration-console.log` y `evidence/integration-restart-console.log`.

Se inspeccionaron capturas a escalas GUI 2 y 3, con pantallas 960x540 y 1280x720.
La plataforma teñida muestra mejoras y los siete objetos cargan en inventario y
mano. No quedan errores de parseo/modelos de Telepads. Alice usa partículas del
mod deshabilitadas; Bob las conserva habilitadas. Todos los viajes pasan en ambos.
Capturas seleccionadas: `evidence/platform.png`, `evidence/name.png`,
`evidence/friends.png`, `evidence/travel.png`, `evidence/scroll.png`, `evidence/empty.png`.

## Herramientas e instancias conservadas

- JDK portátil: `../.tools/jdk/jdk-25.0.4.1+1/`.
- Descargas comprobadas: `../.tools/downloads/`.
- Fuentes de APIs consultadas: `../.tools/forge-sources/`.
- Servidor instalado de smoke: `../.tools/test-server/`, localhost:25576.
- Servidor multijugador de aceptación: `../.tools/integration-server/`.
- Cliente/salas de desarrollo: `run/client/`, `run/TelepadAlice/`, `run/TelepadBob/`.
- Dependencias: caché habitual de Gradle del usuario; assets oficiales en el
  directorio `assets/` de la instalación local de Minecraft.

El entorno muestra avisos de OSHI por contadores de rendimiento de Windows y de
Log4j al inspeccionar clases nativas Netty de Linux/BSD. Las ejecuciones alcanzan
la partida, completan las comprobaciones, guardan y salen correctamente. No se
modificaron el registro de Windows ni bibliotecas de Forge para silenciarlos.

La primera tentativa de GameTests ejecutó solo el test incorporado de Minecraft;
se excluye de la evidencia. El registro y selección explícitos se corrigieron.
También se corrigieron un resultado obsoleto del yunque y el registro tardío del
tintado del objeto, y se repitieron las comprobaciones afectadas.

No quedan funciones de las cuatro especificaciones pendientes. La validación se
limita a las versiones indicadas y mundos nuevos; no incluye migración de 1.19.2
ni una garantía de compatibilidad con otros mods.
## Color crafting acceptance — 2026-10-03

`26.3-7.1.0-dev` / color protocol 2: 14 passing JUnit tests and 9 passing GameTests.
Actual crafting menus verify all sixteen dyes, Minecraft's blend, preserved
components and exact consumption through ordinary taking and shift-click.
Placement, update packets, saves, drops, partial/full overrides, washing,
inventory overflow and upgrade recovery pass.

Two real Forge clients verify default, uniform, mixed, legacy and partial palettes
in crafting previews, inventory and placed blocks, including reconnection and
dedicated-server restart. Local screenshots confirm that the turquoise motif
retains its color. Both old-client/new-server and new-client/old-server connections
reject `telepads:main` during configuration, before a player enters the world.

The clean production JAR contains five recipes and the custom dye serializer,
seven item definitions, all assets and notices, and no acceptance classes.
All 87 runtime files match the tested acceptance build. Evidence for this release
is local in `docs/evidence/dye-colors/`; reproduce it with `color-acceptance.ps1`
and `verify-color-artifact.ps1` as described in `development.md`.

The original port acceptance above is historical. The current publication logo
was subsequently replaced with a cropped in-game screenshot; that asset and
attribution change means the repackaged JAR has a new hash. Publication checks
and the current artifact hash are recorded in `PUBLISHING.md`.

