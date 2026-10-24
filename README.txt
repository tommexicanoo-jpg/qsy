RULETA DEL CAOS - mod de Fabric para Minecraft 1.21.1
=====================================================

COMO COMPILARLO
1) Instala JDK 21 y Gradle 8.8+ (o copia el wrapper "gradlew" de cualquier plantilla de Fabric).
2) En esta carpeta ejecuta:   gradle build
3) El .jar queda en build/libs/ruleta-del-caos-1.0.0.jar (NO uses el que termina en -sources).
4) Ponlo en la carpeta mods junto con Fabric Loader y Fabric API (1.21.1).

Si falla la version de Fabric API, mira la actual en https://fabricmc.net/develop
y cambia fabric_version en gradle.properties.

ALTERNATIVA FACIL: genera un proyecto vacio en https://fabricmc.net/develop/template
(1.21.1, Mojang mappings) y copia dentro la carpeta src/ de este proyecto.

SECTORES (sentido horario desde arriba)
 Naranja  - Cambio de dificultad (Facil/Normal/Dificil al azar)
 Roja     - Momento revi: 3 min, dificultad Dificil + monstruos extra + barra de jefe
 Rosa     - Efecto loco para todos (salto, velocidad, levitacion, ceguera...)
 Morada   - Teletransporte aleatorio a 40-120 bloques
 Azul     - Cambio de clima u hora
 Cian     - Bendicion o maldicion
 Verde    - Aviso (coordenadas, vida, proxima ruleta...)
 Amarilla - Regalo de objetos para todos

COMANDOS (necesitan permisos / trucos activados)
 /ruleta            gira una al azar
 /ruleta roja       fuerza un sector (naranja, roja, rosa, morada, azul, cian, verde, amarilla)

El mod debe estar instalado en el servidor Y en los clientes.
