# Desarrollo

`clean build` y las pruebas JUnit funcionan con esta carpeta como proyecto
independiente. Los scripts PowerShell de smoke/integración y generación de
recursos documentan el entorno original: requieren instancias de servidor,
herramientas o la referencia antigua fuera del repositorio. No son necesarios
para compilar los recursos ya incluidos. `docs/evidence/` es evidencia local
excluida de Git; no se publica junto a las fuentes.

## Catálogo e identidad

`TelepadCatalog` utiliza el `SavedDataStorage` del Overworld y el identificador
`telepads:catalog`. El formato se guarda con `version: 1`, entradas del mundo y
preferencias por UUID de jugador. Los índices se reconstruyen al leer: por UUID
y por dimensión con namespace y coordenadas. Dos nombres iguales son válidos;
dos entradas en la misma ubicación son rechazadas durante la lectura.

Cada colocación crea una identidad nueva, incluso si antes había un telepad en
esa ubicación. La nueva entrada sustituye a la anterior en ambos índices. La
destrucción conserva la entrada como ausente. Descargar un chunk no la convierte
en ausente: se usa `BlockEntity.preRemoveSideEffects`, que solo recibe la retirada
del bloque. Los identificadores olvidados anteriores no ocultan la nueva entrada.

Las entradas y preferencias son inmutables. Las operaciones del catálogo marcan
`SavedData` como modificado inmediatamente; no se exponen colecciones mutables.
Amigos y destinos olvidados pertenecen a un jugador en un mundo, sin depender de
su entidad de jugador ni de su clonación al morir.

## Nombre inicial

La colocación registra inmediatamente al actor con nombre `Telepad`. El servidor
crea un contexto de nombre de 30 segundos, ligado al jugador, identidad y ubicación.
Para confirmar se comprueban dimensión, distancia, bloque real, identidad y usuario.
Confirmar consume el contexto; cerrarlo conserva el nombre predeterminado.
Las peticiones de red solo llevan intención, token, nombre limitado a 16 caracteres
y opción de compartir. Los UUID de amigos se resuelven desde datos del servidor.

## Comprobaciones reproducibles

Con Java 25 seleccionado como indica el README, desde `Telepad26.3/`:

```powershell
.\gradlew.bat build
.\gradlew.bat -PtelepadsGameTests runGameTestServer
.\scripts\server-smoke.ps1 -Mode write
.\scripts\server-smoke.ps1 -Mode read
```

Los GameTests solo se incluyen en el source set con `-PtelepadsGameTests`.
El JAR de distribución se compila sin esa propiedad.
El smoke test usa la instancia aislada `../.tools/test-server/`, ligada a localhost
en el puerto 25576. Conserva su mundo de prueba y comprueba un bloque con nombre
e identidad después de cerrar y reiniciar el servidor.

## Viaje y protocolos

`ActivationSessions` conserva espera y sesión por UUID de jugador/origen. Lee la
espera vigente en cada tick, abre una sola vez y expira el token a los 1200 ticks.
Salida, cambio de origen, cancelación, muerte, cambio de dimensión y logout limpian
el estado temporal; nunca borran preferencias persistentes.

El canal `telepads:main`, protocolo 2, exige la misma versión en ambos extremos
y define dirección y codecs explícitos.
Las solicitudes se ejecutan en el hilo principal del servidor. Nombres: 16
caracteres; amigos: nueve; respuesta de destinos: 64 filas por página, sin truncar
el catálogo completo. El cliente solicita páginas/dimensiones por índice y acciones
por UUID, sin proporcionar coordenadas, permisos, precios ni inventario.

Antes de viajar se comprueban token, origen físico y su identidad, reglas actuales,
acceso al destino y pertenencia a la oferta. Se consume la sesión antes del intento
de movimiento. Se prepara el chunk y se revalidan bloque/identidad/redstone. La
búsqueda prueba como máximo 441 posiciones en un radio horizontal de tres y
vertical de cuatro: apoyo, cuerpo de pie, ausencia de líquidos, altura y borde.
`getChunk` no instala tickets permanentes propios del mod.

`ServerPlayer.teleportTo` del MDK llama al evento cancelable de Forge, también
para viajes dentro de la dimensión. Solo su éxito habilita el cobro de XP o el
consumo portátil. Los puntos disponibles se calculan desde nivel y barra; no se
confía en `totalExperience`. Niveles tienen prioridad sobre puntos.

Configuración y portable usan el mismo servicio de llegada, con coste explícito
cero. Configuración no necesita receptor/transmisor; portable requiere un bloque
real autorizado de la dimensión actual. El collar ordena por distancia espacial;
la bead elige uniformemente entre candidatos válidos. Tintes/drops usan el
componente registrado `telepads:colors`; las mejoras se devuelven aparte.

El color de objetos se registra durante `RegisterColorHandlersEvent.Block`, antes
de cargar modelos. Hacerlo en el setup de cliente podía competir con el parseo de
recursos. Las respuestas tardías de páginas no reabren una activación cerrada.

## Colores de fabricación

`telepads:colors` conserva `frame` y `base` en `[-1,15]` y su constructor de dos
IDs. Añade `frame_rgb` y `base_rgb` opcionales de 24 bits, y `craft_dyes`, una lista
inmutable vacía o de exactamente ocho IDs en `[0,15]`. Los datos antiguos sin
estos campos se leen como antes. Un RGB requiere el ID `-1` en esa parte y un
registro de ocho tintes; si ambos RGB existen, son iguales. El helper devuelve
ARGB opaco. La misma instancia completa pasa por componentes implícitos,
colocación, drops, guardado y paquetes de actualización del bloque.

La receta especial `telepads:telepad_dye` exige 3×3, telepad central y ocho slots
con `DataComponents.DYE`. Copia una unidad del centro y sustituye solo los colores.
`DyedItemColor.applyDyes(null, dyes)` mezcla una muestra por slot, sin incorporar
colores anteriores. El menú vanilla consume ingredientes y gestiona shift-click;
no hay remainders ni receta de resultado fijo en el libro.

El primer tinte directo reemplaza el marco y el segundo el borde; el registro
compartido desaparece cuando ya no queda ningún RGB. El lavado devuelve ese
registro una vez, más los IDs directos actuales, y limpia los colores antes de
entregar objetos. El historial nunca se acumula. Las mejoras siguen separadas.

El stream de protocolo 2 lleva dos bytes de IDs, dos indicadores de RGB opcional
con enteros de 32 bits y un tamaño de registro limitado a 0 u 8 antes de leer sus
bytes. Requiere `26.3-7.1.0-dev` en cliente y servidor. Para volver a una versión
anterior después de guardar mezclas, restaurar también el respaldo del mundo.

## Aceptación de artefactos

```powershell
.\gradlew.bat -PtelepadsGameTests -PtelepadsClientSmoke runClient
.\scripts\integration-smoke.ps1
.\scripts\integration-smoke.ps1 -Restart
.\gradlew.bat clean build
```

La integración empaca una variante instrumentada en
`../.tools/integration-server/mods/telepads-acceptance.jar`. Dos procesos gráficos
Forge cargan ese JAR mediante `telepadsPackagedJar`, omitiendo compilación y copia
de recursos durante el lanzamiento. El servidor instalado carga el mismo JAR
desde `mods/`. Los hooks de `src/gametest/` preparan datos, pulsan controles y
comprueban resultados observables; solo se activan con propiedades optativas.
El servidor de prueba está ligado a localhost:25577 y usa usuarios offline de
prueba. El último `clean build` excluye todas esas clases y datos del JAR entregado.
Se comparan hashes de las clases/recursos de producción entre ambos artefactos.

El script puede repetirse sobre su propio mundo de prueba. `-Restart` verifica los
datos existentes tras reiniciar y requiere haber ejecutado primero el recorrido.
Los logs de aceptación se conservan en `docs/evidence/` antes de limpiar `build/`.

Para los colores de `26.3-7.1.0-dev`, `scripts/color-acceptance.ps1 -Mode initial`
crea un mundo aislado en `../.tools/color-server/`, ejecuta dos clientes y los
reconecta. `-Mode restart` comprueba ese mundo después de reiniciar; conserva
fixtures predeterminado, rojo uniforme, rojo/azul mezclado, dos IDs antiguos y
mezcla con override parcial. Ambos clientes verifican componentes de previews,
inventario y bloques, y guardan capturas. `-Mode old-client` y `-Mode old-server`
usan el JAR instrumentado anterior de protocolo 1, conservado localmente en
`../.tools/telepads-protocol-1-acceptance.jar`, y un mundo de handshake separado.
Estos scripts requieren la instalación Forge local de la aceptación del port.
Los lanzamientos de JAR desactivan `net.minecraftforge.gradle.merge-source-sets`
para evitar que clases locales compiladas se mezclen con la versión seleccionada.
`scripts/verify-color-artifact.ps1` compara el runtime de producción con el JAR
instrumentado probado, verifica los cuatro modos y excluye todas las clases de
aceptación. La evidencia se guarda en `docs/evidence/dye-colors/`.
