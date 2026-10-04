# Guía de juego

Manual de la adaptación independiente para Minecraft Java 26.3 y Forge 66.0.9.

## Nombre, acceso y amigos

Al colocar un telepad, se registra inmediatamente con el nombre `Telepad` para
quien lo coloca. El formulario permite cambiarlo por un nombre de hasta 16
caracteres. Cerrar sin confirmar conserva el nombre predeterminado. Confirmar
requiere seguir cerca del mismo bloque y hacerlo dentro de 30 segundos.

Los telepads nuevos son privados. Para descubrir uno ajeno, visitarlo e
interactuar agachado con la mano principal vacía. Repetir la interacción retira
solo tu registro. Un telepad público informa que no admite ese cambio individual.

La tecla predeterminada **punto (`.`)** abre la lista de amigos; puede reasignarse
en los controles de Minecraft. Escribir el nombre de un jugador conectado y usar
**Add**. Se admiten hasta nueve amigos distintos; **Remove** elimina uno y
**Clear list** vacía tu propia lista. El servidor resuelve su identidad por UUID.
En el formulario inicial, **Share with my friends** da acceso a los amigos que
existan al confirmar. Eliminarlos de la lista después no revoca ese acceso.

El catálogo, permisos, amigos y destinos olvidados se guardan por mundo y UUID.
Cambiar de dimensión, reconectar o morir no los transfiere a otro mundo ni los
borra. Romper un telepad deja un destino ausente; recolocarlo crea otra identidad.

## Instalación y primer viaje

Instalar Minecraft Java **26.3** y Forge **66.0.9**, con Java **25**. Copiar el JAR
de `build/libs/` a `mods/` en cada cliente y en el servidor dedicado. Usar la
misma versión del mod en ambos lados. Crear un mundo nuevo; no se ofrece
importación automática de mundos ni datos de Telepads 1.19.2.
Si el servidor usa whitelist, añadir a cada jugador desde su consola con
`whitelist add NombreDelJugador` antes de conectar.

1. Obtener dos telepads con la receta siguiente o desde la pestaña Telepads en
   creativo. Colocar el primero, confirmar `Home`, y colocar el segundo lejos,
   confirmando `Mine`.
2. Permanecer sobre `Home` tres segundos. Elegir `Mine`; desde `Mine`, esperar
   y elegir `Home` para volver. La rueda desplaza las filas; los botones cambian
   dimensión o página. Los nombres repetidos representan destinos distintos.
3. Cancelar libera la activación. Salir, cambiar de plataforma, morir o
   desconectarse también reinicia la espera. Las sesiones caducan a los 60 segundos;
   salir y volver permite abrir otra selección.

Los costes predeterminados son cero. `xpLevels` tiene prioridad sobre `xpPoints`
si ambos son positivos. Se verifica toda la experiencia disponible entre niveles;
el cobro ocurre una sola vez después de un viaje exitoso. Un destino bloqueado,
sin acceso, sin espacio de llegada o cancelado por Forge conserva la experiencia.

Un destino destruido aparece como **Missing**. Abrirlo permite **Travel to saved
location** o **Forget for me**. Confirmar usa la ubicación guardada por el servidor
y el coste habitual; olvidar solo lo oculta para ti. Se requiere una dimensión
existente y una llegada con apoyo, espacio y límites válidos.

## Recetas y objetos

Las recetas conservan los ingredientes principales de la referencia:

| Objeto | Patrón / ingredientes |
| --- | --- |
| Telepad | `GGG / EEE / ICI`: G vidrio, E Ender Pearl, I bloque de hierro, C brújula |
| Transmitter | `III / RDR / III`: I lingote de hierro, R redstone, D diamante |
| Toggler | `RBR / DDD`: R repetidor, B bloque de redstone, D redstone |
| Necklace | Sin forma: dos Ender Beads y tres hilos |
| Teñir Telepad | `DDD / DTD / DDD`: T telepad, D cualquier tinte; requiere ocho |

En una mesa de fabricación, poner un telepad en el centro y un tinte en cada
uno de los ocho espacios alrededor. Se aceptan los 16 tintes, repetidos o mezclados.
La vista previa muestra un telepad cuyo marco y borde de la base comparten la
mezcla de Minecraft; el anillo y la runa turquesa conservan su color. Cada resultado
consume un telepad y un tinte de cada espacio, conserva el nombre y otros datos
del objeto, y funciona con shift-click. Las cantidades de las pilas no alteran
la mezcla. Repetir la receta sustituye los colores usando solo los ocho tintes
nuevos. La cuadrícula 2×2, un anillo incompleto o un telepad fuera del centro no sirven.

Con `enableAnvilConversion=true`, poner Ender Pearls en **ambas** entradas de
un yunque. El total debe estar entre dos y ocho: produce el doble de Ender Beads,
consume las dos pilas completas y cobra tantos niveles como perlas. Por ejemplo,
dos más dos perlas producen ocho beads y cuestan cuatro niveles. Nueve perlas
o una opción deshabilitada no ofrecen resultado.

Usar una bead elige al azar un telepad utilizable de la dimensión actual. El
collar elige el más cercano por distancia espacial. Requieren acceso, presencia,
estado activo y llegada segura, sin estar sobre una plataforma ni pagar XP.
En supervivencia, un viaje exitoso consume una unidad; el collar devuelve uno
o dos hilos al inventario, o los deja en el suelo si no caben. Fallar conserva el
objeto. El servidor puede desactivar cada objeto por separado.

## Mejoras y personalización

Aplicar un **Transmitter** con clic derecho instala la mejora una vez y consume
uno en supervivencia. Permite elegir destinos de otras dimensiones desde ese
origen; el receptor no necesita otro transmisor. Aplicar un **Toggler** hace que
una señal de redstone desactive el telepad como origen y destino; quitarla lo
reactiva. Las señales no afectan a plataformas sin toggler.

La herramienta **Public Access Tool**, obtenible en creativo o mediante comandos,
alterna público/privado. Solo admite jugadores creativos u operadores de nivel 2
o superior. Volver a privado conserva todos los registros anteriores.

El servidor bloquea por defecto la salida desde plataformas del End mientras
su dragón siga vivo. `blockEndWhileDragonAlive=false` desactiva esa regla; se
aplica también a destinos administrados y se comprueba al confirmar el viaje.

Aplicar un tinte directamente al bloque colorea primero el marco y después el
borde de la base; las siguientes aplicaciones reemplazan el borde. Cada cambio
consume un tinte en supervivencia. En un telepad fabricado con mezcla, la primera
aplicación conserva la mezcla del borde y la segunda la sustituye.

Un cubo de agua restaura ambos colores y devuelve una sola vez los ocho tintes
del último crafteo mientras alguna parte conserva la mezcla, además de los tintes
directos actuales. Por ejemplo, cuatro rojos y cuatro azules devuelven esos ocho;
tras una aplicación directa verde lima, devuelven también ese tinte. Si ambas
partes se reemplazan directamente, solo se devuelven los dos tintes directos
actuales. Rehacer la receta reemplaza el registro anterior; lavar de nuevo un
telepad sin color no crea tintes. En supervivencia deja un cubo vacío; en creativo
conserva el cubo de agua. Los tintes que no caben se dejan en el suelo y las mejoras
instaladas permanecen. Romper con una herramienta
apropiada devuelve un bloque con los colores y una unidad por mejora instalada.
Recolocarlo conserva colores, permite nombrarlo de nuevo y comienza sin mejoras.

## Configuración y destinos administrados

Forge crea `telepads-server.toml` en `serverconfig/` del mundo y
`config/telepads-client.toml` en cada cliente. El servidor gobierna:
`waitSeconds=3`, `xpLevels=0`, `xpPoints=0`, `blockEndWhileDragonAlive=true`,
`enableEnderBead=true`, `enableEnderBeadNecklace=true`, `enableAnvilConversion=true`
y `destinations=[]`. La espera se lee en cada activación; los costes se leen al
confirmar. El cliente controla `particles=true`; apagarlo conserva todos los viajes.

`destinations` acepta cadenas `x/y/z/dimension/nombre`. Ejemplos:

```toml
destinations = [
  "0/100/0/minecraft:overworld/Tower",
  "-20#-10/80/10#20/minecraft:the_nether/Nether area",
  "-100#100/random/-100#100/minecraft:overworld/Surface"
]
```

Los intervalos son inclusivos y ordenados; admiten negativos. `random` funciona
en X/Z, Y o dimensión. La Y aleatoria busca la superficie de la dimensión elegida.
Los valores respetan altura y borde; los intervalos se limitan a su intersección
con esos límites. Preparar apoyo y espacio en los ejemplos de Y fija: el mod
no construye una plataforma. La búsqueda de llegada se limita a tres bloques
horizontales y cuatro verticales alrededor del punto; una búsqueda fallida se
diagnostica sin coste y sin tickets permanentes de chunk.

La **Configured Destination Tool**, también restringida a creativo/operador,
recorre las cadenas configuradas con clic derecho sobre el bloque y después vuelve
al modo normal. Al completar la espera viaja al destino elegido sin receptor,
selector, transmisor ni XP. Una definición inválida da un diagnóstico; si se
elimina la selección al recargar configuración, vuelve al modo normal con aviso.

## Presentación y créditos

La plataforma es un modelo bajo simplificado: marco/base teñibles, dos marcas
azules para transmisor, esquina roja para toggler y barra roja cuando está
desactivada. Se reutilizan los seis iconos originales; no se reproducen el renderer
animado ni las pantallas de 1.19.2. Las pantallas y mensajes usan claves traducibles
con cobertura `en_us`.

Licencia del mod: GPL-3.0-only, en `LICENSE.md`; atribución en `NOTICE.md`.
La licencia del scaffolding Forge está en `FORGE-LICENSE.txt`.
