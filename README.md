# Planificador escolar compartido

React / TypeScript ofrece un espacio de trabajo docente en español con un calendario adaptable de fechas exactas, edición según la titularidad y confirmación explícita de importaciones. Java 21 / Spring Boot proporciona persistencia autenticada para una escuela compartida, importaciones acumulativas de datos heredados y exportaciones mensuales reales en XLSX/PDF. No se ha realizado ningún despliegue en la nube.

## Ejecución local

1. Configure `JAVA_HOME` con JDK 21. Defina `PLANNER_ADMIN_USERNAME`, `PLANNER_ADMIN_PASSWORD` y `PLANNER_SCHOOL_NAME` en el entorno de su proceso local, nunca en el control de versiones. Las contraseñas requieren 12+ caracteres y un máximo de 72 bytes UTF-8. El valor opcional `PLANNER_SCHOOL_YEAR` es 2026 por defecto y `PLANNER_GRADE` es 6.
2. Desde la raíz del espacio de trabajo, ejecute `./backend/mvnw.cmd -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev` con la configuración de caché de Maven indicada más adelante. El perfil explícito `dev` utiliza la base de datos H2 local persistente en `backend/.tools`; no sustituye una base de datos de producción.
3. El primer inicio con una base de datos vacía crea una escuela, un administrador y el contexto académico configurado. Solo el grado 6 de PRIMARY recibe el catálogo público de sexto grado con cuatro áreas; los demás contextos comienzan vacíos. Los inicios posteriores nunca restablecen credenciales ni sobrescriben la planificación existente. Retire los secretos de inicialización después del aprovisionamiento. Una base de datos vacía sin variables de inicialización permanece inaccesible; no hay registro público de usuarios.
4. En una segunda terminal, ingrese a `frontend`, ejecute `npm ci` y luego `npm run dev`. Abra la URL local que muestra Vite (normalmente `http://127.0.0.1:5173`) e inicie sesión con la cuenta aprovisionada. Vite redirige `/api` mediante un proxy a `127.0.0.1:8080`; las credenciales y los datos privados de planificación no se guardan en localStorage. No hay un acceso de demostración ni un plan guardado ficticio.

La interfaz utiliza Tailwind CSS 4 y componentes básicos Button/Input/Card/Dialog con el patrón de shadcn, mantenidos localmente. Los diálogos utilizan Radix para el foco del teclado, Escape y la restauración del foco; los botones utilizan Radix Slot y CVA. Compile con `npm run build`. El Dockerfile de la raíz compila React e incorpora sus recursos compilados en el JAR de Spring Boot **únicamente durante la construcción de la imagen**, de modo que la interfaz y `/api` comparten un mismo origen. El desarrollo local sigue utilizando Vite; no se copian archivos generados de la interfaz al código fuente del servidor.

Producción es el perfil predeterminado y requiere `PLANNER_DATABASE_URL` (una URL JDBC de PostgreSQL), `PLANNER_DATABASE_USERNAME` y `PLANNER_DATABASE_PASSWORD`. Nunca recurre a H2 como alternativa. Antes de un despliegue público, utilice HTTPS, un proxy inverso del mismo origen, acceso privado a la base de datos, copias de seguridad, gestión de secretos y límites de frecuencia para los inicios de sesión y las solicitudes. Las sesiones de acceso almacenadas mediante JDBC sobreviven a los reinicios y admiten varias instancias que comparten la misma base de datos. La cookie de sesión es HttpOnly, SameSite=Strict y Secure en producción. No se habilita ningún comodín de CORS. El desarrollo local con React debe redirigir `/api` mediante un proxy al puerto 8080 en lugar de utilizar cookies entre orígenes distintos.

## Preparación del despliegue en Render

Los archivos de despliegue están preparados localmente; **no constituyen evidencia de un despliegue exitoso en la nube ni de que el sistema esté listo para producción**. El Blueprint crea un servicio web Docker gratuito y ninguna base de datos. No despliegue hasta disponer de un servicio PostgreSQL duradero, autorizado explícitamente, y de sus credenciales.

1. Publique únicamente el código fuente de la aplicación, la configuración de compilación y las pruebas automatizadas sintéticas en el repositorio privado autorizado. Nunca cargue `sources/` sincronizados, bases de datos locales de `.tools/`, exportaciones, credenciales, `node_modules`, `dist` ni registros del flujo de trabajo local. `.gitignore` los excluye y `.dockerignore` permite únicamente las entradas necesarias para construir la imagen.
2. Seleccione el repositorio en Render utilizando la sesión de cuenta autorizada. Mantenga el servicio en el plan de instancia Free. `render.yaml` desactiva los despliegues automáticos; los despliegues posteriores siguen siendo decisiones explícitas.
3. Proporcione los tres valores `PLANNER_DATABASE_*` mediante la configuración de variables de entorno secretas de Render. La URL debe ser una URL **JDBC** de PostgreSQL, no una URI `postgres://` sin adaptar. Utilice la configuración TLS/CA verificada del proveedor de la base de datos, preferiblemente `sslmode=verify-full`; no desactive la verificación de certificados ni incluya contraseñas en la URL, el código fuente, los argumentos de construcción de la imagen o los registros. La base de datos debe ser accesible desde Render conforme a su política de red configurada explícitamente.
4. Inicialice una escuela vacía únicamente mediante credenciales temporales de administrador proporcionadas por separado, como se describe en el inicio local. El Blueprint no contiene deliberadamente ninguna cuenta ni contraseña de inicialización. Retire los secretos de inicialización después del aprovisionamiento. Sin aprovisionamiento, la aplicación puede mostrar la pantalla de acceso, pero nadie puede iniciar sesión.
5. Valide las migraciones y la persistencia de sesiones en PostgreSQL, el inicio de sesión/CSRF/cierre de sesión por HTTPS, los límites de titularidad, la confirmación de importaciones y exportaciones XLSX/PDF representativas en el servicio desplegado antes de confiarle datos escolares.

| Configuración | Límite |
|---|---|
| Entorno de ejecución | Proceso Java 21 sin privilegios de administrador; perfil `prod`; escucha en `0.0.0.0` utilizando la variable `PORT` de Render |
| Rutas públicas | GET/HEAD de `/`, `/index.html` y `/assets/**`; la planificación autenticada permanece en `/api/**`; el inicio y cierre de sesión siguen requiriendo CSRF |
| Confianza en el proxy | El Blueprint habilita `SERVER_FORWARD_HEADERS_STRATEGY=framework` para el punto de entrada de Render que termina HTTPS. Esto confía en las cabeceras reenviadas: no permita entradas directas o no confiables al puerto de la aplicación y verifique que el perímetro reemplace o depure las cabeceras de reenvío proporcionadas por el cliente. Fuera de este límite de confianza, deje la variable sin definir (`none`). |
| Cookies | Secure, HttpOnly y SameSite=Strict sin cambios; abra la aplicación directamente como página HTTPS de nivel superior. No se admite ni se ha verificado el uso incrustado o en iframe entre sitios. |
| Memoria | Memoria dinámica máxima de 256 MiB con límites de memoria nativa de la JVM, 20 hilos de trabajo HTTP y 5 conexiones a la base de datos. Son límites iniciales para una instancia de 512 MiB, no resultados de una prueba de carga de exportaciones; las exportaciones concurrentes o muy grandes todavía pueden agotar la memoria. |
| Comprobación de salud | `/` confirma que se sirve la estructura de la interfaz; no comprueba si la base de datos está lista. Las migraciones de inicio requieren la base de datos. |

El servicio gratuito de Render puede suspenderse tras un periodo de inactividad y pierde los cambios del sistema de archivos local. No utilice H2 ni copias de seguridad en archivos allí. Su oferta gratuita de PostgreSQL vence después de 30 días, por lo que se ha omitido deliberadamente de este Blueprint. El alojamiento gratuito sirve para una vista previa o un proyecto personal, no garantiza fiabilidad ni un servicio de producción. Consulte los [límites de las instancias gratuitas](https://render.com/docs/free), el [despliegue con Docker](https://render.com/docs/docker), la [vinculación de puertos y entrada TLS](https://render.com/docs/web-services), los [secretos de Blueprint](https://render.com/docs/blueprint-spec#prompting-for-secret-values) y el [manejo de proxies en Spring](https://docs.spring.io/spring-boot/how-to/webserver.html).

**Todavía se requiere:** selección autorizada de una base de datos duradera, evidencia de integración con PostgreSQL, comprobaciones de suplantación de cabeceras de reenvío en el punto de entrada real, límites de frecuencia para acceso/solicitudes/exportaciones, verificación de memoria y carga, copias de seguridad operativas cifradas con restauración probada, recuperación de contraseñas y una prueba básica del despliegue. La construcción Docker omite las pruebas; ejecute por separado las comprobaciones indicadas más adelante antes de desplegar. No se dispuso de un demonio Docker para verificar la imagen localmente durante la preparación.

## Planes independientes por año, nivel y etapa

Utilice los selectores de **Plan académico** para abrir un año, nivel y edad/grado existentes. Los administradores pueden desplegar **Crear plan para otro año, nivel o etapa**, seleccionar, por ejemplo, el grado 3 de Primaria en 2026 o 2027 y confirmar **Crear plan vacío**. Cada contexto comienza vacío: primero cree sus áreas en **Escuela y horarios** y luego sus temas personalizados en **Banco de temas**. Los temas, fechas, bloqueos y docentes responsables pueden editarse de forma independiente en cada plan; no se copia ningún currículo automáticamente. Los docentes pueden abrir planes existentes, pero solo los administradores pueden crearlos. La edad cero de Inicial es **Menores de 1 año**, nunca grado cero.

Al cambiar de contexto se borran los datos actuales, el filtro de área, el editor y la vista previa de importación. Las solicitudes y los formularios capturan el ID explícito del contexto; las cargas tardías, las funciones ejecutadas al completar una modificación y las descargas de una selección anterior no pueden sustituir el espacio de trabajo actual. Los usuarios de la escuela siguen perteneciendo a toda la escuela. La barra lateral, el encabezado del espacio de trabajo y las exportaciones mensuales identifican el contexto seleccionado.

La escuela es la identidad de la organización, no un año académico. Un contexto identifica una escuela/año/nivel/etapa. Los miembros autenticados de la escuela consultan `GET /api/contexts`; los administradores crean contextos mediante `POST /api/contexts` con `{year,level,stageValue}`. Los años van de 1900–2200; los niveles son `INITIAL` (edades 0–5), `PRIMARY` (grados 1–6) y `SECONDARY` (grados 1–5). `INITIAL` utiliza `stageKind: "AGE"`, no un grado; la edad cero es "Menores de 1 año", con ciclos I (0–2) y II (3–5). Los demás niveles utilizan `stageKind: "GRADE"`. Los metadatos incluyen `id,schoolId,year,level,stageKind,stageValue,cycle,displayLabel,revision,legacyDefault`. No se ha implementado la eliminación de contextos ni la copia automática del currículo.

Utilice `?contextId=<id>` explícitamente en **todas** las solicitudes de recursos de planificación, incluidas creación/actualización/eliminación, `/plan`, `/imports`, `/imports/preview` y `/imports/confirm`. Los ID de contexto nunca seleccionan una escuela: la autenticación siempre proporciona la organización. Se rechazan los ID de recursos o elementos superiores que pertenecen a otro contexto, incluso dentro de la misma escuela. Las versiones de sesión siguen siendo locales a cada recurso; las revisiones de vistas previas y los bloqueos de filas son locales al contexto, por lo que un año no invalida la vista previa de otro. Las fechas y los bloqueos deben corresponder al año del contexto seleccionado. Los bloqueos de toda la escuela pasan a ser bloqueos de todo el contexto.

`GET /api/plan?contextId=<id>` devuelve los metadatos del contexto indicados anteriormente, además de `state` (mapas heredados) y `warnings`. Las respuestas de sesiones/recursos y el historial de importaciones incluyen `contextId`. El importador heredado en sí no cambia y se ofrece únicamente a los contextos de grado 6 de `PRIMARY`, porque los metadatos de las copias antiguas no permiten identificar un nivel. Los temas personalizados siguen disponibles en todos los contextos; la validación habitual de sesiones no depende de ese importador.

Flyway V3 crea un contexto PRIMARY a partir del año/grado almacenado de cada escuela histórica y conserva todos los ID de recursos, ID heredados, titulares, valores/versiones de sesión, bloqueos, historial de importaciones y revisiones. Los grados históricos ambiguos fuera de 1–6 o los años no admitidos hacen fallar la migración en lugar de adivinarse. Las migraciones anteriores no cambian. Los contextos nuevos comienzan vacíos; el catálogo público de sexto grado se carga solo durante la primera inicialización de PRIMARY de grado 6. La variable opcional de inicialización `PLANNER_LEVEL` tiene PRIMARY como valor predeterminado, mientras que `PLANNER_GRADE` significa edad para INITIAL y grado para los demás niveles.

**Límite temporal de compatibilidad:** las consultas que omiten el contexto y las antiguas sobrecargas de servicios seleccionan únicamente el contexto predeterminado inmutable de migración/inicialización, nunca el año más reciente ni una escuela proporcionada por quien llama. Los ID de contexto explícitos vacíos o ajenos nunca recurren al valor predeterminado. La interfaz siempre envía un ID de contexto explícito, incluidas las solicitudes de exportación e importación. Solo los clientes antiguos que omiten el parámetro conservan la compatibilidad con el contexto predeterminado; un ID explícito vacío o ajeno se rechaza. `/me.school.schoolYear` y `grade` son metadatos históricos de inicialización, no un contexto de planificación seleccionado. El campo transitorio `grade` del plan es nulo para INITIAL. La instantánea v4 de toda la escuela abarca todos los contextos, pero la restauración automática sigue sin estar admitida; no constituye un sistema probado de recuperación ante desastres.

## Contrato de la API para la interfaz

Todas las rutas están bajo `/api`. Las solicitudes GET autenticadas permiten lectura en toda la escuela; los ID de escuela proporcionados por quien llama nunca seleccionan la organización. Las modificaciones devuelven el objeto guardado; las eliminaciones devuelven una respuesta 200 vacía. Utilice JSON excepto para iniciar sesión. Los ID de recursos son cadenas. Las respuestas de listados son arreglos, con nombres en camelCase.

1. GET `/csrf` → `{token,headerName}`. Envíe el token en esa cabecera en **cada** modificación, incluidos el inicio y cierre de sesión, con cookies del mismo origen.
2. POST `/login` como `application/x-www-form-urlencoded` con `username` y `password` → 200 o 401. Obtenga un token CSRF nuevo después de iniciar sesión. GET `/me` → `{id,schoolId,username,role,school}`.
3. POST `/logout` → 204; luego obtenga un token CSRF nuevo. Las solicitudes anónimas a recursos protegidos devuelven 401; la ausencia de CSRF o la denegación de permisos devuelve 403.

| Recurso | GET para listar; POST para crear; PUT `/{id}` para actualizar; DELETE `/{id}` | Campos de la solicitud |
|---|---|---|
| `/areas` | Solo el administrador puede realizar modificaciones | `name, ownerId, sharedEdit` |
| `/topics` | Editores de áreas propias o compartidas | `areaId, name, unit` (1–99), `legacyId` opcional (`course\|unit\|zeroBasedIndex`; inmutable después de la creación) |
| `/schedules` | Editores de áreas propias o compartidas | `areaId, weekday` (ISO: lunes=1 a domingo=7), `startTime, endTime` (`HH:mm`, inicio anterior al fin) |
| `/sessions` | Editores de áreas propias o compartidas | `topicId, assignment, status, date, notes`; la actualización requiere además un `version` numérico |
| `/blocks` | Editores de áreas propias o compartidas; los bloqueos de toda la escuela requieren administrador | `areaId` (nulo para toda la escuela), `label, kind, startDate, endDate`; el tipo es `EXAM`, `VACATION`, `HOLIDAY` u `OTHER` |
| `/users` | Solo GET y POST del administrador | `username, password, role` (`ADMIN` o `TEACHER`); las respuestas nunca contienen hashes de contraseñas |

Las respuestas de sesiones utilizan `exactDate` (la solicitud utiliza `date`) e incluyen `version`, inicialmente 0. Elimine una sesión con `?version=<current version>`. Una edición/eliminación desactualizada → 409 `STALE_SESSION`; recargue en lugar de sobrescribir. Hay una sesión por tema en el contexto académico seleccionado. La fecha exacta debe estar vacía/ser nula o ser una fecha ISO válida dentro del año de ese contexto. La asignación utiliza los nombres de meses heredados en español y el estado está vacío o es `Trabajado`. Nulo significa ausente; las cadenas vacías siguen siendo valores vacíos explícitos. Un PUT de sesión reemplaza sus cuatro campos, por lo que la interfaz debe enviar todos los valores actuales.

Por defecto, los docentes solo pueden modificar sus áreas asignadas. Un administrador puede habilitar `sharedEdit` por área para permitir que cualquier docente de la **misma escuela** la edite. Solo los administradores asignan titulares de áreas o gestionan usuarios. Las referencias entre escuelas se rechazan con 404 (o 403 cuando toda la operación requiere un administrador). Las áreas/temas referenciados no pueden eliminarse hasta retirar sus recursos dependientes; el servidor devuelve 409, no una pérdida de datos en cascada.

Los intervalos del calendario son inclusivos y deben permanecer dentro del año del contexto académico seleccionado. Guardar una sesión en un bloqueo aplicable devuelve 409 `BLOCKED_DATE`; la fecha nunca se desplaza. Agregar un bloqueo sobre una sesión existente conserva esa sesión y devuelve `warnings`; GET `/plan` también devuelve los solapamientos actuales. La interfaz debe mostrarlos. No se presuponen ni insertan automáticamente feriados legales o fechas de exámenes.

### Vista previa y confirmación de importaciones

- GET `/plan` → `{revision,year,grade,state,warnings}`. Los cuatro mapas heredados incluyen solo temas vinculados al catálogo. GET `/sessions` incluye también los temas personalizados.
- POST `/imports/preview` con `{json:"<raw backup text>"}` → `{revision,result:{state,preview,warnings},notice}`. El estado combinado devuelto es solo una vista previa. Muestre el aviso de prioridad de los datos entrantes y los conteos antes de confirmar.
- POST `/imports/confirm` con `{json:"<same raw backup text>",revision,acknowledgeIncomingWins:true}` → `{revision,result}`. La confirmación vuelve a validar y calcular sobre el plan actual bloqueado; las vistas previas desactualizadas devuelven 409 `STALE_PREVIEW` y requieren otra vista previa. Todos los temas afectados deben ser editables y las fechas bloqueadas rechazan la transacción completa. El historial se escribe únicamente cuando la transacción se confirma correctamente.
- GET `/imports` devuelve el historial de la escuela `{id,schoolId,userId,importedAt,affectedTopics,revision}`; las copias privadas sin procesar nunca se conservan en el historial ni en los diagnósticos.

Los errores de validación utilizan 400; los errores del importador proporcionan `{code:"INVALID_IMPORT",violations:[{path,code}]}`. Los errores de conflicto/autorización proporcionan `{code:<HTTP status>,message}`. Los ID/nombres duplicados o las eliminaciones de elementos referenciados devuelven 409 `RESOURCE_CONFLICT`. Las restricciones de la base de datos también exigen referencias a elementos superiores de la misma escuela. Flyway valida las sumas de comprobación de las migraciones; JDBC utiliza sentencias explícitas, sin DDL automático de ORM ni creación alternativa del esquema de producción.

## Verificación

Desde `frontend`, ejecute `npm test` y `npm run build`. Las comprobaciones específicas de Vitest cubren la ubicación en el calendario por fecha exacta, días con alta concentración de sesiones y fines de semana, titularidad, CSRF nuevo después de la autenticación, mensajes de edición desactualizada y consentimiento de vista previa/confirmación. El formato del código fuente se comprueba con `npm exec -- prettier --check 'src/**/*.{ts,tsx,css}' '*.json' '*.ts' index.html`.

Ejecute desde la raíz del espacio de trabajo en PowerShell, con una instalación de JDK 21 disponible:

```powershell
# Set JAVA_HOME to your JDK 21 root if the existing value points to another version.
$env:MAVEN_USER_HOME = Join-Path $PWD 'backend/.tools/maven'
$env:MAVEN_OPTS = '-Dmaven.repo.local="' + (Join-Path $PWD 'backend/.tools/repository') + '"'
.\backend\mvnw.cmd -f backend/pom.xml -Dtest=LegacyPlanImporterTest test
.\backend\mvnw.cmd -f backend/pom.xml verify
```

La primera ejecución descarga la distribución de Maven y las dependencias públicas desde Maven Central. Las distribuciones del contenedor de Maven y las cachés de dependencias permanecen en el directorio ignorado `backend/.tools` al utilizar esta configuración. Los resultados generados de compilación permanecen en `backend/target`.

PDFBox puede examinar las fuentes instaladas; delimite su caché opcional agregando `-Dpdfbox.fontcache=<absolute backend/.tools path>` a las comprobaciones locales de Maven o a la JVM de la aplicación. Las pruebas de exportación utilizan únicamente datos sintéticos y escriben muestras de control de calidad en `backend/.tools/export-qa`. Analizan celdas reales de libros de cálculo y texto PDF, y renderizan páginas PDF. Las pruebas de integración HTTP necesitan permiso para enlazar y conectarse a sockets de bucle local.

## Exportaciones y límites de las copias de seguridad

Todas las rutas de exportación requieren la sesión autenticada de la escuela, utilizan filtrado por organización determinado por el servidor y devuelven `Cache-Control: no-store`. Son descargas GET, por lo que no requieren una cabecera CSRF de modificación. Los ID de contexto de otras escuelas y los ID de área de otro contexto devuelven 404. Utilice un `contextId` explícito para todas las exportaciones mensuales y v2.

| Ruta | Resultado |
|---|---|
| `/api/exports/monthly?contextId=<context>&month=3&areaId=<id>&format=xlsx` | Libro OOXML real generado con Apache POI; omitir el área selecciona todas las áreas de ese contexto |
| `/api/exports/monthly?contextId=<context>&month=3&areaId=<id>&format=pdf` | Documento paginado de PDFBox con calendario de días laborables y anexos completos |
| `/api/exports/backup?contextId=<context>&month=3&areaId=<id>&format=json` | Estructura contenedora solo del catálogo de grado 6 de PRIMARY, versión v2, con mapas heredados independientes; omita el mes para obtener el plan anual completo vinculado al catálogo de ese contexto |
| `/api/exports/backup?contextId=<context>&format=full-v4` | Instantánea de versión 4 de **toda la escuela y todos los contextos**, incluidos cada año/nivel/etapa y todas las áreas/temas, sesiones, horarios, bloqueos e historial de importaciones; ignora los filtros de mes/área después de validar las referencias proporcionadas |
| `/api/exports/backup?format=full` | **v3 obsoleta: solo el contexto predeterminado original.** Se conservan el esquema y el nombre del alcance existentes para clientes antiguos; NO es una instantánea escolar completa de múltiples contextos. Se rechaza un contexto explícito distinto del predeterminado; utilice v4 |

Los meses van de 1–12. XLSX conserva la geometría A:J del libro de referencia: cinco pares de código/tema por día laborable, título y metadatos combinados, una fila semanal de fechas exactas y al menos cuatro filas de contenido que se amplían cuando hace falta. El PDF utiliza los mismos cinco pares de días laborables con páginas de continuación. Ningún formato oculta temas bajo los bloqueos ni desplaza fechas automáticamente. Las sesiones de sábado/domingo, eventos escolares, horarios, elementos sin programar, conflictos de mes/fecha y notas completas aparecen en los anexos. La selección mensual incluye un tema cuando coincide su fecha exacta **o** su mes asignado, además de los temas sin mes/fecha; una fecha en conflicto fuera del mes seleccionado se muestra en el anexo, no se reubica en la cuadrícula. Solo se exportan eventos escolares confirmados; no se insertan supuestos de exámenes o feriados de referencia.

Las notas XLSX se dividen en filas de anexos antes de alcanzar el límite de tamaño de celda de Excel. El texto PDF se ajusta y pagina; los caracteres fuera de la fuente estándar de PDF se representan de forma reversible como `[U+XXXX]`, nunca se descartan silenciosamente. Los acentos del español se representan con normalidad.

**El alcance de la copia de seguridad importa:** la interfaz identifica v2 como **Catálogo heredado del plan · v2** y v4 como **Toda la escuela · todos los planes · v4**. V2 está disponible únicamente para grado 6 de PRIMARY, respeta los filtros de área/mes seleccionados, excluye temas personalizados y el importador acumulativo existente solo lo acepta en un contexto compatible. La interfaz bloquea explícitamente las importaciones heredadas en otros niveles/etapas.

V4 tiene `{format:"shared-school-planner",version:4,scope:"complete-school-all-contexts",schoolName,restoreSupported:false,contexts:[...]}`. Cada entrada de contexto contiene metadatos `context` y arreglos separados de `areas,topics,sessions,schedules,blocks,history`; cada fila conserva su `contextId` y sus referencias. La escuela autenticada es la autoridad y todos los contextos se leen en una única transacción de lectura repetible. Se excluyen contraseñas, sesiones de usuario/acceso, credenciales y archivos de importación sin procesar. V3 sigue siendo una exportación obsoleta solo del contexto predeterminado original; su versión y esquema existentes no se ampliaron silenciosamente.

**V3/V4 son instantáneas solo de exportación.** El importador v1/v2 no las acepta; no se han implementado la restauración automática de instantáneas completas ni las copias de seguridad operativas cifradas de la base de datos. Conserve estas exportaciones para un proceso deliberado de migración/recuperación, no como garantía de recuperación con un clic.

Referencias de dependencias: [Apache POI 5.5.1](https://poi.apache.org/download.html), [API de hojas de cálculo de POI](https://poi.apache.org/components/spreadsheet/quick-guide.html), [Apache PDFBox 3.0.8](https://pdfbox.apache.org/download.cgi), [configuración de PDFBox](https://pdfbox.apache.org/3.0/getting-started.html).

## Contrato de importación

Cree `LegacyPlanImporter` con el catálogo autorizado de ID conocidos `course|unit|zeroBasedIndex`, el año escolar y el grado. Llame a `preview(json, existingState)` para obtener una instantánea combinada inmutable, conteos por mapa y advertencias. La vista previa no persiste nada; un servicio autenticado futuro debe autorizar y guardar el resultado.

| Entrada o comportamiento | Contrato |
|---|---|
| Datos sin envoltura | Objeto que contiene cualquier subconjunto de `assignments`, `status`, `dates`, `notes` |
| Datos con envoltura | `{ "version": 2, "year": 2026, "grade": 6, "state": { ... } }`; los metadatos pueden omitirse, pero los presentes deben coincidir con la política configurada; solo versiones 1 y 2 |
| Mapas | Los mapas ausentes están vacíos; los presentes deben ser objetos con valores de cadena; se rechazan `null`, arreglos y valores que no sean cadenas |
| Combinación | Las claves entrantes tienen prioridad de forma independiente en cada mapa; las claves y mapas omitidos conservan los datos existentes; las cadenas vacías explícitas se conservan, nunca se tratan como ausentes |
| Meses | De `Marzo` a `Diciembre`; `Septiembre` se convierte en `Setiembre`; se conserva la cadena vacía |
| Estado | La cadena vacía significa pendiente; `Trabajado` significa completado; `Pendiente` es una etiqueta de visualización heredada, no un estado almacenado |
| Fechas | Cadena vacía o fecha estrictamente válida `yyyy-MM-dd` dentro del año escolar configurado; las fechas exactas nunca se desplazan para coincidir con las asignaciones |
| Notas | Texto conservado exactamente, incluidos los espacios en blanco y las cadenas vacías |
| ID o campos desconocidos | Se rechaza toda la vista previa con infracciones estructuradas de ruta/código; sin resultados parciales ni temas descartados silenciosamente |
| JSON ambiguo | Se rechazan claves duplicadas y valores JSON adicionales al final |
| Límites de seguridad | Máximo de 2,000,000 caracteres de entrada, 100,000 caracteres por cadena, profundidad de anidamiento 16 y longitud numérica 16; se rechaza en lugar de truncar |

Los conteos describen los **campos entrantes**, no un plan de reemplazo: `incoming = added + updated + unchanged`. `explicitEmpty` cuenta los valores entrantes de cadena vacía y `affectedTopics` es la unión de los ID de temas entrantes de los cuatro mapas. Las advertencias describen la instantánea resultante: `MONTH_DATE_CONFLICT` conserva la fecha exacta y `UNASSIGNED_TOPIC_DATA` conserva los datos independientes sin un mes asignado.

## Procedencia y alcance

- La base se generó con Spring Initializr para Java 21. Su versión padre generada `4.1.1.RELEASE` no existía en Maven Central; se utiliza en su lugar la versión publicada `4.1.1`.
- Jackson 2 sigue siendo explícito para el límite de compatibilidad puro, la carga del catálogo y los adaptadores internos de importación. HTTP utiliza la pila JSON predeterminada de Spring Boot.
- Las pruebas utilizan únicamente cuentas y copias de seguridad sintéticas. El archivo incluido `catalog/legacy-grade6.json` contiene solo el catálogo público de temas `DATA`, obtenido en modo de solo lectura del HTML original y conservando sus ID; no se copió ninguna copia de seguridad docente ni estado del navegador. El HTML original y todos los `sources/` sincronizados permanecen intactos.
- La publicación del repositorio excluye bases de datos locales de ejecución, referencias sincronizadas, credenciales y resultados generados. El código y los datos sintéticos de pruebas automatizadas son únicamente fuentes de verificación; nunca son cuentas iniciales de producción ni datos docentes de ejecución.

## Próximo paso

Seleccione y autorice PostgreSQL duradero; luego verifique el despliegue HTTPS/proxy, los límites de frecuencia y las operaciones de copia de seguridad/restauración en el entorno autorizado. Las pruebas de integración con H2, una compilación de la interfaz y la configuración de despliegue no demuestran que el sistema esté listo para producción. No se realizó ninguna migración de copias de seguridad de docentes reales.
