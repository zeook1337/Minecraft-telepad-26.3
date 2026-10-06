# Verificaciones pendientes de 1.0.1

El candidato sigue en **Alpha, sin publicar**. Las pruebas manuales quedan a
cargo del usuario. No marques una prueba como aprobada por haber compilado el
JAR o por encontrar un resultado histórico. Conserva el mismo JAR durante cada
sesión y registra su SHA-256, la revisión de fuentes, el entorno y el resultado.
La entrega local incluye `SHA256SUMS.txt`, `handoff.json` y fuentes completas.
La revisión inmutable de esa entrega identifica el candidato; no es una
publicación alojada ni una aprobación de Release.

Ejecuta los comandos desde `Telepad26.3` con PowerShell 7. Los mundos y las
instancias indicados son copias de pruebas; conserva intactos tus mundos normales
y los respaldos originales. Los nombres de instancia/evidencia deben ser nuevos.

## Instalaciones normales, usando exclusivamente el JAR limpio

- [x] Verifica `Get-FileHash <ruta-del-JAR> -Algorithm SHA256` contra la entrega.
  Confirmado por el usuario: coincide con
  `BBC71248A9393F7E5BC958676C969777F35712DB2DFC18AC732130CA700A3E57`.
- [ ] Instala Minecraft Java 26.3 / Forge 66.0.9 / Java 25. Usa el mismo candidato
  en servidor y clientes. No agregues el ayudante de fixtures a estas pruebas.
- [ ] En un mundo individual nuevo: fabrica los telepads y objetos, coloca y
  nombra un telepad y comprueba que aparecen los modelos, colores y recetas.
- [ ] Confirma que `Share with server` comienza desmarcado. Cancelar o dejarlo
  desmarcado mantiene el pad privado. Marcarlo publica para otro usuario sin
  amistad ni registro y para un usuario que entra después.
- [ ] Configura costes de XP. Prueba un viaje exitoso y otro rechazado; comprueba
  el cobro único y la conservación de posición, XP e inventario al fallar.
- [ ] Prueba Overworld, Nether y End con el Transmitter y la regla del dragón.
  Prueba Toggler/redstone y un destino configurado por el administrador.
- [ ] Mezcla ocho tintes, cambia parcialmente el color, guarda y recarga. Lava
  una vez y vuelve a lavar: los tintes se recuperan una sola vez.
- [ ] Usa bead y collar: consumen uno; el collar devuelve una o dos cuerdas y no
  cobra XP. Un viaje sin llegada segura conserva todos los recursos.
- [ ] Guarda y recarga el mundo individual. En un servidor dedicado normal,
  repite los flujos con dos usuarios, desconecta/reconecta y reinicia el servidor.
  Comprueba nombres, permisos, colores, mejoras y destinos ausentes.

## Compatibilidad: siete combinaciones

Todas las versiones antiguas están preservadas en `.tools/release-baselines`
con sus hashes originales. Usa instalaciones y mundos separados para probarlas.
El rechazo debe ocurrir durante configuración, antes de entrar al mundo, y
nombrar la incompatibilidad de Telepads (`telepads:main`). Conserva logs de
cliente y servidor; no basta con que una ventana se cierre.

| Cliente | Servidor | Resultado esperado | Pendiente |
| --- | --- | --- | --- |
| 1.0.1, protocolo 3 | 1.0.1, protocolo 3 | Entra; permisos privados/públicos y texto correctos | [ ] |
| Referencia protocolo 1 | 1.0.1 | Rechazo antes de entrar | [ ] |
| 1.0.1 | Referencia protocolo 1 | Rechazo antes de entrar | [ ] |
| Publicado 26.3-7.1.0-dev, protocolo 2 | 1.0.1 | Rechazo antes de entrar | [ ] |
| 1.0.1 | Publicado 26.3-7.1.0-dev, protocolo 2 | Rechazo antes de entrar | [ ] |
| Alpha 1.0, protocolo 2 | 1.0.1 | Rechazo antes de entrar | [ ] |
| 1.0.1 | Alpha 1.0, protocolo 2 | Rechazo antes de entrar | [ ] |

El ayudante `release-compatibility.ps1 -Mode all` automatiza estas combinaciones.
Su carga gráfica sigue pendiente de una ejecución funcional; no hay resultados
de conexión aprobados para el candidato. Consulta `development.md` para el
lanzador opcional basado en una instalación real de Forge.

## Mundos actualizados: reconexión y acciones del usuario

La automatización compara datos y bloques de copias de ambos respaldos, vuelve
a guardar, reinicia y restaura otras copias con cada JAR original. Sus resultados
no sustituyen estas acciones con clientes reales:

- [ ] En otra copia del fixture publicado, reconecta propietario y amigo:
  `Baseline0` conserva ambos registros y sigue privado. Un extraño o nuevo
  usuario no lo ve. Los nombres sintéticos originales son `TelepadAlice`,
  `TelepadBob` y `TelepadCarol` en un servidor de pruebas offline en localhost.
- [ ] En otra copia del fixture Alpha 1.0, `Baseline1` sigue público. Comprueba
  que los demás pads mantienen sus permisos, identidades y dimensiones.
- [ ] Comprueba amigos, el destino ausente/olvidado, colores directos y mixtos,
  la mezcla con cambio parcial, lavado único, ambas mejoras y el destino
  configurado. Guarda, desconecta/reconecta y reinicia antes de repetir.
- [ ] Usa una copia adicional para lavar o cambiar estado: el manifiesto
  original debe conservarse para las comparaciones de actualización.

## Carga con cuatro clientes y resistencia

La compilación del ayudante, la creación de 1.000 destinos en las tres dimensiones
y su persistencia se verifican por separado. El bucle de cuatro clientes y la
ejecución larga aún necesitan una sesión real. No uses el ayudante de aceptación
en una instalación de producción ni en las pruebas normales anteriores.

```powershell
$javaHome = '..\.tools\jdk\jdk-25.0.4.1+1'
$candidate = '..\.tools\release-candidates\1.0.1\telepads-26.3-1.0.1.jar'
$helper = '..\.tools\release-final-helper-3\telepads-fixture-helper.jar'
$runtime = '..\.tools\installed-forge-client-runtime-exported.json'
$run = Get-Date -Format 'yyyyMMdd-HHmmss'
.\scripts\release-scale.ps1 -JavaHome $javaHome `
  -ForgeDirectory ..\.tools\test-server -CandidateJar $candidate `
  -HelperJar $helper -InstalledClientConfiguration $runtime `
  -InstanceRoot "..\.tools\release-scale-quick-$run" `
  -EvidenceDirectory "docs\evidence\release-readiness\1.0.1\scale-quick-$run" `
  -Mode quick
```

- [ ] Completa primero una sesión corta: cuatro clientes reales, viajes
  simultáneos exitosos/rechazados, XP, páginas, privacidad, las tres dimensiones,
  llegada a chunk descargado, bead/collar y reconexión de los cuatro usuarios.
  Revisa los logs individuales. `quick` siempre deja resistencia incompleta.
- [ ] Con otras rutas nuevas, repite con `-Mode endurance`: diez minutos de
  calentamiento y **cuatro horas continuas medidas**, más guardado y reinicio.
  Registra hardware, heaps y Java. Mantén los cuatro clientes activos, salvo las
  reconexiones breves programadas individualmente.
- [ ] Revisa `endurance-evaluation.json`: cada ventana de cinco minutos debe
  sostener al menos 19 TPS; la mediana post-GC de la última hora no debe superar
  en más de 20% la primera. Se requieren muestras naturales suficientes y
  operaciones de cada familia en todas las horas.
- [ ] No debe haber crash, pérdida de datos, duplicación, cobro doble, exposición
  privada ni llegada insegura. El reinicio final debe conservar los datos.

También puedes conectar cuatro clientes de fixtures por tu cuenta usando
`-ManualClients`, los nombres `ScaleAlice`, `ScaleBob`, `ScaleCarol`, `ScaleDave`,
el candidato y el ayudante, y la propiedad JVM `-Dtelepads.releaseScale=true`.
Conecta a `127.0.0.1:25582`. Esta opción no configura ni cierra tus clientes.
El lanzador automático utiliza cuatro instancias nuevas y un heap de 1 GiB por
cliente; el servidor usa 2 GiB por defecto. Un timeout es FAILED.

## Registro y decisión final

Para cada sesión conserva localmente:

```json
{
  "candidateSha256": "SHA-256 del JAR de la entrega",
  "sourceRevision": "revisión inmutable de handoff.json",
  "environment": {"minecraft": "26.3", "forge": "66.0.9", "java": "25"},
  "gate": "identificador de la comprobación",
  "status": "PASS, FAILED o NOT RUN",
  "observations": "pasos, resultado y defectos pendientes"
}
```

Guarda logs, identificadores, capturas y rutas privadas en `docs/evidence/` o
`.tools/`. Para publicar, actualiza solo el resumen sanitizado en `acceptance.md`.
Cuando todos los gates estén aprobados para el mismo candidato, revisa la
decisión y regenera la entrega y sus fuentes. Hasta entonces conserva Alpha.
