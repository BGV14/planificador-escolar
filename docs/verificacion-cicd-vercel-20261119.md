# Verificación de CI y despliegue con Vercel

Fecha de preparación: 19 de noviembre de 2026.

## Integración continua

El workflow `.github/workflows/ci-cd-planificador.yml` se ejecuta en pull requests y pushes hacia `main`. Verifica el backend con Java 21 y Maven, y el frontend con Node 20, Prettier, Vitest y la compilación de Vite.

## Conexión del repositorio

El proyecto `planificador-escolar` de Vercel está conectado a `BGV14/planificador-escolar`. Los despliegues anteriores a esta conexión se publicaron mediante la CLI y no acreditan por sí solos un despliegue automático originado por GitHub.

## Prueba de trazabilidad

Este cambio documental permite verificar el flujo sin modificar la lógica de la aplicación:

1. Abrir un pull request contra `main`.
2. Revisar el resultado de los jobs de backend y frontend en GitHub Actions.
3. Comprobar si Vercel genera un despliegue de vista previa asociado al commit del PR.
4. Con las comprobaciones pertinentes finalizadas, integrar el PR.
5. Registrar el despliegue de producción generado por el commit de integración, su estado y su URL.

La evidencia completa debe mostrar el commit, la rama, el resultado del build y el estado del despliegue. La conexión del repositorio no garantiza que la configuración de construcción sea correcta ni que Vercel espere el resultado de GitHub Actions.

## Diferencia respecto de Render

Los bloques comentados de Docker y Render del workflow son una preparación independiente. La integración Git de Vercel utiliza su propia configuración de construcción y publicación; no activa esos bloques.

## Enlaces de comprobación

- [GitHub Actions](https://github.com/BGV14/planificador-escolar/actions)
- [Despliegues en Vercel](https://vercel.com/bgv-14-team-s/planificador-escolar/deployments)
- [Repositorio conectado en Vercel](https://vercel.com/bgv-14-team-s/planificador-escolar/settings/git)

Los resultados efectivos de esta prueba deben consultarse en el PR y en los registros de ambas plataformas. No se declara un despliegue exitoso antes de observar su finalización.
