# Publicar en GitHub y CurseForge

Revisión: 3 de octubre de 2026. Alcance: esta carpeta `Telepad26.3` como proyecto
independiente. La revisión busca exposición de datos en archivos publicables y
en el JAR; no constituye una auditoría exhaustiva de vulnerabilidades del mod.

Repositorio público: [Minecraft-telepad-26.3](https://github.com/zeook1337/Minecraft-telepad-26.3).
Descarga de desarrollo: [v26.3-7.1.0-dev](https://github.com/zeook1337/Minecraft-telepad-26.3/releases/tag/v26.3-7.1.0-dev).
Fuentes correspondientes: [tag v26.3-7.1.0-dev](https://github.com/zeook1337/Minecraft-telepad-26.3/tree/v26.3-7.1.0-dev).

## Resultado y límites de la revisión

No se encontraron credenciales incorporadas en el código o la configuración del
proyecto. El README anterior y el registro de aceptación incluían rutas con el
usuario del equipo: se retiraron de la documentación publicable. Los logs y las
evidencias locales también contienen rutas; se excluyen junto con instancias,
mundos, cachés, configuraciones locales, claves y archivos de entorno.

Se revisaron el JAR de producción existente y el reconstruido: no se detectaron
rutas personales ni los patrones de secretos buscados, y no contenían los hooks
de aceptación. Ambos incluían la licencia, los créditos y los avisos de Forge.
La entrega actual es `26.3-7.1.0-dev`, con mezcla de ocho tintes y protocolo de
red 2. El JAR de `build/libs/` se reconstruyó con la nueva imagen y atribución.

No existía un repositorio `.git` en esta carpeta ni en la raíz del proyecto al
iniciar la revisión. No había historial local de commits que analizar. Si luego
se importa otro historial o se añaden archivos, revisarlos antes de publicar.
Las búsquedas de patrones no garantizan detectar cualquier secreto arbitrario.

**Un `.gitignore` solo excluye archivos de las operaciones de Git.** No limpia
una carpeta comprimida manualmente ni debe asumirse que el cargador web de GitHub
lo aplica. Los archivos locales siguen en tu disco. Publica mediante Git o usa
un archivo de fuentes creado a partir de los archivos elegidos por Git.
Tampoco oculta información en archivos que ya estén versionados.
[Referencia: GitHub, ignorar archivos](https://docs.github.com/en/get-started/git-basics/ignoring-files).

Los nombres de paquetes, créditos de los autores, versiones y UUID de pruebas
sintéticos forman parte de las fuentes. No son credenciales. La revisión no
necesita eliminar estos elementos ni las licencias.

## GitHub: preparar solo esta carpeta

Abre una terminal dentro de `Telepad26.3`. El repositorio debe contener directamente
`build.gradle`, `src/`, `gradle/`, `README.md` y las licencias. No hace falta subir
la carpeta padre, el proyecto 1.19.2, herramientas descargadas o OpenSpec.

Antes del primer commit, configura un nombre público y la dirección `noreply`
exacta que GitHub muestra en **Settings → Emails**, si quieres ocultar tu correo.
El correo forma parte de los metadatos de los commits; el `.gitignore` no lo
oculta. [Referencia: correo de los commits](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address).

```powershell
git init
# Configura aquí tu identidad pública y el correo noreply real de tu cuenta.
git add .
git status --short
git diff --cached --stat
git diff --cached
```

Revisa antes de hacer el commit: no deben aparecer `run/`, `logs/`, `.gradle/`,
`build/`, `docs/evidence/`, `.env`, claves ni ajustes locales del servidor.
**Sí deben aparecer** el wrapper JAR de Gradle, las fuentes, recursos, pruebas,
scripts y licencias. El README utiliza rutas relativas del repositorio.

Para la publicación inicial, el repositorio se configura con rama `main`,
remoto `origin` y correo `noreply` de GitHub. Las actualizaciones posteriores
deben revisar los cambios antes de crear un commit y enviarlo al mismo remoto.
Las fuentes de cada binario distribuido deben conservarse en su tag correspondiente.

Para producir un ZIP de fuentes que respete exactamente el commit:

```powershell
git archive --format=zip --output=../telepads-source.zip HEAD
```

Ese ZIP sirve como archivo de fuentes; la descarga principal del mod en
CurseForge será el **JAR compilado**.

## CurseForge: requisitos de la ficha

Necesitas una cuenta y crear el proyecto desde el
[panel de autores](https://authors.curseforge.com/#/projects/create/choose-game).
Selecciona Minecraft y la clase **Mods**. Completa nombre, resumen, descripción,
licencia, categorías y avatar; después añade el archivo y espera la moderación.
[Guía oficial de creación](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

La ficha debe cumplir lo siguiente:

- Nombre distintivo, en inglés, sin números de versión o información técnica.
  `Telepad` ya es el nombre del original; también existe otro port no oficial.
  El título de trabajo del README no garantiza disponibilidad del nombre.
- Resumen y descripción con inglés primero. Pueden incluir español después.
- Explicar funciones, instalación, requisitos y diferencias respecto al original;
  redactar texto propio y enlazar/créditar al autor original.
- Avatar propio, cuadrado, PNG de 400 × 400 píxeles. No reutilizar como avatar de
  la nueva ficha la imagen de otro proyecto o un logo genérico del juego.
- Changelog descriptivo para cada archivo. Enlaces promocionales, si se añaden,
  al final; no sustituir la descripción con enlaces externos de descarga.

[Políticas de moderación](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies)
y [guía de envío](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips).

**Avatar preparado:** [curseforge-logo.png](media/curseforge-logo.png), PNG de
400 × 400, es un recorte centrado en el telepad de la captura suministrada.
Se usa también en `src/main/resources/logo.png`; reemplaza el logo del original.
La [captura completa](media/telepad-in-game.png) sirve para la galería y README.
Ambas se exportaron sin metadatos incrustados y sin IA ni retoques de la escena.
La procedencia y el recorte están registrados en `media/logo-provenance.json`.
**Pendiente:** escoger y comprobar la disponibilidad del nombre final de la ficha.

Capturas reales de este port ayudan a mostrar el bloque y sus pantallas. Los mods
no requieren la galería adicional que se exige a texture packs; para contenido
visual revisa también la política de muestras. Antes de usar capturas locales,
revísalas por nombres, chats o datos personales. Si una imagen de presentación
modificada con IA puede representar incorrectamente el contenido del juego,
CurseForge exige indicarlo claramente.
[Creación](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project),
[moderación](https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies).

## Licencia y autor original

El [proyecto original](https://www.curseforge.com/minecraft/mc-mods/telepad) publica
GPLv3, y el repositorio local conserva su texto. Esta adaptación declara
`GPL-3.0-only`: conserva `LICENSE.md`, `NOTICE.md` y `FORGE-LICENSE.txt`, y selecciona
**GNU General Public License version 3 (GPLv3)** en la ficha. No cambies a MIT o
«All Rights Reserved» sin resolver los derechos del contenido reutilizado.

Al distribuir el binario bajo GPLv3 debes facilitar el código fuente completo
correspondiente. Una ruta práctica para esta entrega es publicar las fuentes de
esta carpeta, crear un tag para el commit exacto de cada JAR y señalar ese tag o
archivo fuente junto a la descarga. Mantén accesible esa versión; enlazar solo
a una rama que cambia no identifica necesariamente el código del binario.
Incluye las fuentes, recursos y archivos necesarios para construirlo.
[FAQ oficial de GNU](https://www.gnu.org/licenses/gpl-faq.en.html).

Usar GPLv3 permite un port que cumpla sus condiciones, pero no supone respaldo
del autor original ni aprobación automática de CurseForge. La nueva ficha debe
presentarse explícitamente como adaptación independiente y conservar los créditos.

## Archivo y etiquetas de esta entrega

| Campo | Valor aplicable al proyecto actual |
| --- | --- |
| Clase | Mods |
| Categoría principal sugerida | Player Transport |
| Categoría adicional sugerida | Technology |
| Minecraft | 26.3, sujeto a que aparezca en el selector del panel |
| Loader | Forge |
| Entorno | Client & Server |
| Java | 25, indicado en descripción |
| Forge probado | 66.0.9 |
| Archivo principal | `build/libs/telepads-26.3-7.1.0-dev.jar` |
| Estado sugerido para el archivo actual | Alpha, porque sigue marcado como desarrollo |
| Dependencias de otros proyectos | Ninguna necesaria según el build actual |
| Licencia | GPLv3 |
| Changelog | Cambios del mod en `CHANGELOG.md` |

El Forge objetivo figura en el [sitio oficial](https://files.minecraftforge.net/net/minecraftforge/forge/index_26.3.html).
No se ha accedido a tu panel de autor: confirma allí que Minecraft 26.3 está
disponible al subir el archivo. Si no está, consulta a soporte; no etiquetes el
JAR como compatible con otra versión. Tampoco marques Fabric o NeoForge.
El Telepad original es una referencia y crédito, **no** una dependencia a instalar.

Elige Alpha/Beta/Release según la estabilidad real. La documentación indica que
se necesita al menos un archivo Release para sincronizar el proyecto con la app;
no cambies el estado a Release solo para obtener esa visibilidad. Completa el
changelog, versiones y relaciones de proyectos en el formulario de cada archivo.
[Guía oficial de archivos](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).

## Descripción breve en inglés lista para adaptar

**Summary:** Craft named teleportation pads, share destinations with friends,
and travel between dimensions with configurable costs.

**Description:**

> This independent Forge adaptation brings Telepads to Minecraft Java 26.3.
> Place and name pads, stand on one to choose a known destination, and share
> access with friends. A Transmitter enables cross-dimension departures; a
> Toggler adds redstone control. Dyeable pads, portable teleport items, configurable
> experience costs, and administrator destinations are included.
>
> Color pads in a crafting table with one pad in the center and eight dyes around
> it (`DDD / DTD / DDD`). Minecraft's dye mixing colors the frame and base rim
> together while the turquoise rune keeps its color. Recrafting replaces the
> blend; washing recovers the latest mixture once while either part still uses it.
> The refreshed platform uses custom pixel textures and an inset Ender-style motif.
>
> Requires Minecraft Java 26.3, Forge 66.0.9, and Java 25. Install the same mod
> version on every client and the dedicated server. No additional mod is required.
> This is a development build intended for new worlds. It does not import legacy
> Telepads data. Version 26.3-7.1.0-dev uses protocol 2 and requires matching builds
> on both sides; previous protocol-1 builds cannot connect. Older palettes from
> this 26.3 port remain readable. Back up your world before updating; downgrading
> after saving mixed colors requires restoring the previous backup.
> The custom block model and rewritten interface differ from the original animated
> presentation.
>
> Original Telepad by Subaraki / AbsolemJackdaw:
> https://www.curseforge.com/minecraft/mc-mods/telepad
> This is an unofficial adaptation, distributed under GPLv3. See the accompanying
> source, license, and attribution notices.

Para esa descripción, usa las
[fuentes de esta entrega](https://github.com/zeook1337/Minecraft-telepad-26.3/tree/v26.3-7.1.0-dev)
y el [enlace de soporte/issues](https://github.com/zeook1337/Minecraft-telepad-26.3/issues).
Las descargas binarias de la ficha de CurseForge deben realizarse a través de
CurseForge; el enlace de GitHub identifica las fuentes y el soporte del port.

## Comprobación final

1. Publicar las fuentes filtradas en GitHub, con identidad pública y notices.
2. Compilar con Java 25: `./gradlew.bat clean build`, sin propiedades de pruebas.
3. Comprobar que el JAR incluye `META-INF/mods.toml`, las tres licencias/notices,
   clases del mod y recursos; que excluye hooks, logs, mundos y datos locales.
4. Crear el tag de las fuentes correspondientes y conservarlo.
5. Elegir el nombre final y usar `docs/media/curseforge-logo.png` como avatar;
   añadir descripción en inglés, captura real y créditos.
6. Subir el JAR como archivo de Mods para Minecraft 26.3 / Forge, con changelog y
   el estado de estabilidad adecuado. Esperar revisión y atender las observaciones.

No subas la carpeta entera, un ZIP del entorno de desarrollo, un instalador Forge
o el JAR instrumentado de aceptación como archivo principal del mod.

## Aceptación archivada de la versión actual

El cambio de tintes archivado registra 14 JUnit y 9 GameTests correctos, pruebas
con menús reales, dos clientes compatibles, reconexión y reinicio del servidor,
y rechazo de pares con protocolos distintos. Sus resultados se resumen en
[acceptance.md](acceptance.md). No se repite la aceptación gráfica al actualizar
la documentación y el logo. Las verificaciones de publicación de abajo se
refieren al paquete reconstruido con esos cambios de presentación.

## Verificación de publicación actualizada — 3 de octubre de 2026

- Inventario de Git temporal, sin inicializar un repositorio en esta carpeta:
  122 archivos publicables, incluidos los nuevos recursos, scripts y medios.
  Ningún patrón de credencial o ruta personal detectado en esos archivos.
- Logs, evidencias, instancias, mundos y cachés siguen excluidos. El wrapper de
  Gradle, fuentes, cinco recetas, recursos, licencias y medios siguen incluidos.
- Copia independiente formada solo por esos archivos: `clean build` correcto con
  Java 25 y `--no-build-cache`; se ejecutaron las 14 pruebas JUnit, todas correctas.
  Las dependencias se resolvieron con la caché existente. No se repitió la
  integración gráfica ni los GameTests; se comprobó el informe local archivado
  de nueve GameTests, sin fallos.
- JAR: 119 entradas, siete definiciones de objetos, cinco recetas, el serializer
  de tintes, nuevo logo y notices. Sin hooks de aceptación ni patrones de datos
  sensibles detectados. De las 87 entradas de producción, solo `logo.png` cambia
  frente al JAR previo a esta actualización; las clases y recursos de juego
  permanecen iguales. La atribución también se actualizó en `META-INF/NOTICE.md`.
- Logo y avatar idénticos, PNG de 400 × 400; captura de 1920 × 1080. Medios
  exportados sin metadatos incrustados. Enlaces relativos comprobados.

El archivo `Telepad26.3-fuentes-publicables.zip`, entregado junto a esta carpeta,
se renovó con los 122 archivos filtrados. Sustituye al ZIP anterior del port.
El ZIP es para publicar las fuentes; el JAR es la descarga principal de CurseForge.

JAR de esta revisión: **330975 bytes**.
SHA-256: `D2DAC004E188CCBDA8113F81991E9EC9F645F0F21877453B0374DC41ACC2DB58`.
El hash cambiará si posteriormente se modifica y reconstruye el proyecto.
