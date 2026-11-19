# CI y despliegue del Planificador Escolar con Vercel

## Fuente de la aplicación

El repositorio debe conservar la misma versión de la aplicación que se publica en producción. El 19 de noviembre de 2026 se recuperaron los archivos de la versión publicada en el despliegue `dpl_2bG3F9ZJPsmhTyZTWkGmhjDvJJKp`. Los 84 archivos de construcción de la copia local coincidieron, mediante SHA-1 de sus bytes, con los identificadores de los archivos de ese despliegue.

La recuperación incluye los planes por sección, la asignación de docentes, la administración de plataforma, las migraciones V4 a V6 y la adaptación de variables de PostgreSQL para Neon. Se conservaron sus pruebas y se excluyeron dependencias, bases locales, credenciales y archivos de ejecución.

## Integración continua en GitHub Actions

El workflow `.github/workflows/ci-cd-planificador.yml` se ejecuta en pull requests y pushes hacia `main`:

- Backend: Java 21 y Maven verify.
- Frontend: Node 20, instalación reproducible, Prettier, Vitest y compilación de Vite.

Los resultados efectivos deben comprobarse en la ejecución asociada al último commit del PR. Un resultado de un commit anterior no verifica cambios posteriores.

## Construcción y publicación en Vercel

`vercel.json` selecciona el framework `container`. `Dockerfile.vercel` compila React y Spring Boot en una imagen con Java 21, sirve ambos desde el mismo origen y expone el puerto 8080. El repositorio conectado permite que Vercel cree vistas previas desde ramas y despliegues de producción desde la rama configurada para producción.

La primera prueba de conexión publicó archivos estáticos y devolvió 404 porque faltaban en GitHub la configuración y parte de la aplicación. Restaurar los archivos de producción corrige esa diferencia de fuente. El estado Ready debe acompañarse de una comprobación HTTP y visual de la aplicación.

## Secuencia de verificación

1. Publicar los cambios en una rama y abrir el PR.
2. Esperar las pruebas del backend y frontend del último commit.
3. Esperar la construcción de la imagen y el despliegue Preview en Vercel.
4. Verificar que la vista previa responde y presenta el acceso al Planificador.
5. Integrar el PR cuando las verificaciones sean satisfactorias.
6. Confirmar que Vercel genera un despliegue Production cuyo commit corresponde a la integración.
7. Registrar las capturas, el commit y los enlaces de las ejecuciones.

La integración Git de Vercel puede construir en paralelo a GitHub Actions. No debe afirmarse que Vercel espera esas pruebas si no hay una configuración explícita que imponga esa dependencia. Los bloques comentados de Docker y Render del workflow son independientes de esta integración.

## Persistencia y secretos

Producción utiliza PostgreSQL y conserva las migraciones de la versión publicada. Las credenciales se administran en las variables del servicio, nunca en este documento ni en los archivos del repositorio. La verificación del despliegue no modifica usuarios ni datos escolares.

## Evidencias

- [PR de recuperación y verificación](https://github.com/BGV14/planificador-escolar/pull/18)
- [GitHub Actions](https://github.com/BGV14/planificador-escolar/actions)
- [Despliegues de Vercel](https://vercel.com/bgv-14-team-s/planificador-escolar/deployments)
- [Conexión Git en Vercel](https://vercel.com/bgv-14-team-s/planificador-escolar/settings/git)
