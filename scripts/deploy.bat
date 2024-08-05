@echo off

setlocal

set ENV_FILE=.\.env

REM Обновление кода и деплой backend приложения
pushd D:\ForPrograms\JavaProject\SpringBootTelegramCloudStorage || exit /b

REM Переходим на ветку main
git checkout dev

REM Обновляем ветку main
git pull origin dev

REM Останавливаем старые контейнеры микросервисов и запускаем новые, с обновлённым кодом
docker-compose -f docker-compose.yml --env-file %ENV_FILE% down --timeout=60 --remove-orphans
docker-compose -f docker-compose.yml --env-file %ENV_FILE% up --build --detach

popd || exit /b

endlocal
