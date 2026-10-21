param([string]$Version='0.5.0')
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
6. Удерживайте ЛКМ для плавания к курсору; WASD — дополнительное управление.
7. Соберите 5 порций пищи и нажмите G — брачный зов. После встречи с партнёром
   откроется экран эволюции. Нажмите «Изменить потомка». V — история эволюции.
8. В редакторе выберите деталь слева и перетащите на объёмную клетку.
   Установленную деталь можно выбрать и перенести. Колесо — размер (0,5–2),
   R — поворот на 15°, кнопка оси — X/Y/Z. ПКМ с движением — свободная орбита, Shift+ПКМ — крен, Home — сброс.
   Shift+колесо — приближение, S — симметрия, Z/Y — отмена/повтор, Del — удалить.
   Стрелки под каталогом переключают страницы деталей.
   Тело собирается из связанных кубиков. Нажмите «Сохранить» для рождения потомка.
9. Для выхода используйте меню паузы → «Сохранить и выйти».
10. Продолжить можно через выбор сохранения: мир называется Spore Cell.

Редактор и интерфейс режима переведены на русский. Новые органы открываются
при подборе золотистых фрагментов метеорита и частей погибших клеток.
Рост больше не открывает хоботок автоматически в новых мирах. Установленные
органы старых сохранений сохраняют доступ. Над атакованной клеткой появляется здоровье, попадания подсвечиваются.
Остальные стадии Spore пока не реализованы. Порог размножения в этой версии —
правило прототипа, полного совпадения всех механик с оригиналом пока нет.
"@ | Set-Content -LiteralPath $readme -Encoding utf8
$archive=Join-Path $project "build/release/SporeCell-$Version-MC1.21.1.zip"
Compress-Archive -LiteralPath @($mods,$readme) -DestinationPath $archive -Force
Get-Item -LiteralPath $archive | Select-Object FullName,Length
Get-FileHash -LiteralPath $archive -Algorithm SHA256
