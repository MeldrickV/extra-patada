---
name: github-ci-android
description: >-
  Cómo validar Xtra for Kick usando GitHub Actions (build, lint y unit tests JVM) en
  lugar de compilar localmente. USAR siempre que haya que verificar cambios, leer logs de CI, o tocar
  los workflows .github/workflows. Prohibido compilar/testear en la máquina local.
---

# CI de Xtra for Kick con GitHub Actions

## Contexto

No se compila ni se testea localmente (ni SDK de Android ni JDK instalados). Todas las validaciones
corren en GitHub Actions sobre el repo `MeldrickV/extra-patada`. El PAT de GitHub está disponible en
el entorno de la sesión; usarlo SOLO vía variable de entorno (nunca escribirlo en archivos/commits).

## Workflows

### `.github/workflows/ci.yml` (gatillado por push/PR a `main`)
- **build-debug** (ubuntu-latest): `assembleDebug` + `compileDebugKotlin`, luego `lintDebug`.
  Sube el APK debug como artifact `apk-debug`. Tiene lint baseline `app/lint-baseline.xml`.
- **unit-tests** (ubuntu-latest): `testDebugUnitTest`; sube reports.

**No hay instrumented tests** (`connectedDebugAndroidTest`): se eliminaron del CI porque el emulador
tardaba 10+ min solo para arrancar con un suite vacío (no había tests en `src/androidTest`) y además
daba timeouts de boot en runners de GitHub. La cobertura de la capa Kick se hace con **unit tests JVM**
(`src/test`): parsing de DTOs kotlinx-serialization, mappers y `PlaylistUtils`. El dispositivo real lo
prueba el usuario en su teléfono; no revivir el job de emulador salvo que haya tests que lo justifiquen.

Comandos clave (Gradle): `assembleDebug`, `compileDebugKotlin`, `lintDebug`, `testDebugUnitTest`.

### `.github/workflows/release.yml` (manual o tag `v*`)
- `assembleRelease`, sube APK release y crea GitHub Release si fue por tag.

## Cómo verificar un push (flujo estándar)

1. Hacer commit y push a rama de trabajo.
2. Consultar el estado del run más reciente:

   ```bash
   curl -s -H "Authorization: token $GITHUB_PAT" \
     "https://api.github.com/repos/MeldrickV/extra-patada/actions/runs?per_page=5" \
     | python3 -c "import json,sys; d=json.load(sys.stdin); [print(r['name'],'|',r['status'],'|',r['conclusion'],'|',r['head_sha'][:8]) for r in d.get('workflow_runs',[])]"
   ```

3. Si hay fallos, obtener los job ids del run:

   ```bash
   curl -s -H "Authorization: token $GITHUB_PAT" \
     "https://api.github.com/repos/MeldrickV/extra-patada/actions/runs/<RUN_ID>/jobs" \
     | python3 -c "import json,sys; [print(j['name'],j['id'],j['conclusion']) for j in json.load(sys.stdin).get('jobs',[])]"
   ```

4. Bajar los logs del job fallido:

   ```bash
   curl -sL -H "Authorization: token $GITHUB_PAT" \
     "https://api.github.com/repos/MeldrickV/extra-patada/actions/jobs/<JOB_ID>/logs"
   ```

5. Corregir, push de nuevo y repetir hasta verde. No declarar nada completado con CI rojo.

## Artefactos

- APK debug: artifact `apk-debug` de cualquier run verde.
- APK release: artifact `apk-release` (workflow release).

## Reglas al tocar los workflows

- Mantener las 3 validaciones (build, lint, unit).
- No romper el `concurrency` group ni quitar `timeout-minutes`.
- Actualizar este skill si cambia la matriz o los comandos.