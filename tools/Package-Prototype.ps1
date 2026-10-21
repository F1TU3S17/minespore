param([string]$Version='0.2.0')
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mod=Join-Path $project "build/libs/spore-cell-$Version.jar"
if(!(Test-Path -LiteralPath $mod)){throw 'Build the mod before packaging.'}
$api=Get-ChildItem -LiteralPath (Join-Path $project '.gradle-home/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.115.1+1.21.1') -Recurse -Filter 'fabric-api-0.115.1+1.21.1.jar' | Select-Object -First 1
if(!$api){throw 'Fabric API is missing from the local Gradle cache.'}
$stage=Join-Path $project "build/release/SporeCell-$Version"
$mods=Join-Path $stage 'mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null
Copy-Item -LiteralPath $mod -Destination $mods -Force
Copy-Item -LiteralPath $api.FullName -Destination $mods -Force
$readme=Join-Path $stage 'Установка.txt'
@"
Spore Cell $Version
Minecraft Java 1.21.1 · Fabric Loader 0.16.10 или новее · Java 21

1. Закройте Minecraft. Откройте папку нужного профиля Minecraft 1.21.1 Fabric.
2. Удалите предыдущий JAR Spore Cell из mods этого профиля.
3. Скопируйте ОБА файла из папки mods этого архива в mods профиля.
   Если подходящий Fabric API уже установлен, оставьте одну его копию.
4. Запустите Minecraft. В главном меню нажмите «Spore: стадия клетки».
5. Выберите травоядную или хищную клетку: создастся отдельный мир.
6. WASD — движение; G — редактор; R — поворот; S — симметрия; Z — отмена.
7. Для выхода используйте меню паузы → «Сохранить и выйти».
8. Продолжить можно через выбор сохранения: мир называется Spore Cell.

Редактор и интерфейс режима переведены на русский. Всеядный рот доступен при
100 прогресса. Остальные стадии Spore пока не реализованы.
"@ | Set-Content -LiteralPath $readme -Encoding utf8
$archive=Join-Path $project "build/release/SporeCell-$Version-MC1.21.1.zip"
Compress-Archive -LiteralPath @($mods,$readme) -DestinationPath $archive -Force
Get-Item -LiteralPath $archive | Select-Object FullName,Length
Get-FileHash -LiteralPath $archive -Algorithm SHA256
