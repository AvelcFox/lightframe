# LightFrame (RGB-освещение для Fabric 1.20.1)

Самостоятельный Fabric-мод, добавляющий **настоящее динамическое RGB-освещение** поверх
ванильного рендера: источники света имеют цвет, интенсивность и радиус, свет «красит»
освещаемые блоки и сущности, несколько источников корректно смешиваются
(красный + синий = фиолетовое освещение). Включает в себя цветные факелы всех оттенков,
поддержку динамического света в руке и оптимизированную очередь обновления света.

**Работает на чистой ваниле — Iris/OptiFine/shaderpack'и не нужны.**
При наличии Iris мод корректно уходит в совместимый fallback (см. ниже).

Полный технический разбор ванильного light engine, рендер-пайплайна и точек интеграции:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Сборка

```bash
./gradlew build          # jar появится в build/libs/lightframe-0.1.0.jar
```

Требуется JDK 17+. Готовый jar кладётся в папку `mods` вместе с Fabric API.

## Возможности

* **ColorLight API** — публичный API для других модов (`dev.puffspark.lightframe.api.ColorLightAPI`):
  создание/удаление источников, изменение цвета, интенсивности, радиуса, позиции,
  временное отключение, перечисление источников.
* **RGB-движок** — собственный движок распространения цветного света (BFS по каналам R/G/B
  с ослаблением по непрозрачности блоков), кэш в разреженных секциях 16³, dirty regions,
  бюджет времени на тик. Никакого пересчёта всего мира: пересборка меша — только затронутых секций.
* **Двухканальная модель**: цвет — умножение в vertexColor при сборке меша (свет реально
  красит геометрию), интенсивность — `max()` с ванильным block light (видно в полной темноте).
  Sky light, факелы и весь ванильный пайплайн сохраняются.
* **Цветные факелы**: 16 классических цветов с крафтом из красителей, частицами и поддержкой стен.
* **Сущности и block entities** — тинтятся от окружающего RGB-света (экспериментально, за конфигом).
* **Iris fallback**: при активном shaderpack'е тинт выключается (чтобы не конфликтовать),
  но динамическая подсветка остаётся — мод не падает и не ломает игру.
* **Debug-инструменты**: визуализация источников и радиусов, HUD со счётчиками
  (источники, dirty-секции, время пропагации, FPS).

## Использование

### Команды

```
/create_light <цвет> [радиус] [интенсивность]     # тестовый источник перед игроком
/lightframe list                                 # список источников измерения
/lightframe remove <uuid>                        # удалить источник
/lightframe clear                                # удалить все
/lightframe debug on|off                         # отладка
/lightframe reload                               # перечитать конфиг
```

Цвета: `red, green, blue, white, purple, yellow, cyan, orange, pink` или `#rrggbb`.

### Клавиши

* **K** — вкл/выкл отладочную визуализацию + HUD.

> **Примечание по правам:** Команда `/create_light` и управление источниками (`/lightframe clear`, `remove`, `reload`) требуют прав оператора сервера (уровень прав 2). Обычные игроки не могут спавнить или удалять источники через команды.

### API (для разработчиков модов)

```java
ColorLight light = ColorLightAPI.create(world, pos, LightColor.RED, 8, 1.0f);
light.setColor(LightColor.PURPLE);
light.setIntensity(2.0f);
light.setPosition(newPos);
light.setEnabled(false);   // временно выключить
light.remove();            // удалить навсегда

for (ColorLight l : ColorLightAPI.getAll(world)) { ... }
```

Источники, созданные на сервере, синхронизируются клиентам автоматически.
Источники, созданные на клиенте (через `clientWorld`), — чисто визуальные.

## Конфигурация (`config/lightframe.json`)

| Ключ | По умолчанию | Значение |
|---|---|---|
| `enableRGBLighting` | `true` | главный выключатель |
| `maxLightSources` | `128` | лимит источников на мир |
| `maxLightRadius` | `32` | лимит радиуса источника |
| `lightingQuality` | `MEDIUM` | `LOW`/`MEDIUM`/`HIGH` — качество тинта |
| `updateBudget` | `2.0` | мс на тик на распространение света |
| `maxSectionsRebuiltPerTick` | `12` | лимит пересборок секций за кадр-тик |
| `tintStrength` | `1.0` | сила цветового тинта (0–1) |
| `tintEntities` | `true` | тинт сущностей |
| `boostVanillaLight` | `true` | подмешивать яркость RGB в ванильный свет |
| `affectGameplayLighting` | `true` | учитывать RGB в игровых запросах света |
| `debugMode` | `false` | debug-визуализация и HUD |

## Архитектура

```
api/       ColorLightAPI, ColorLight, LightColor        — публичный API
block/     ColoredTorchBlock, ColoredWallTorchBlock     — цветные факелы
engine/    RGBLightEngine, LightPropagation,            — RGB-движок
           LightStorage, LightUpdateQueue, LightNode
world/     ChunkLightStorage, LightSection              — разреженное RGB-хранилище
render/    VanillaLightingBackend, TintingVertex...,    — ванильный рендер-бэкенд
           ClientEngineListener
iris/      IrisCompat                                   — Iris-детекция и fallback
config/    ColorLightConfig
net/       ColorLightNetworking                         — сервер→клиент синхронизация
debug/     LightDebugRenderer, DebugHud
mixin/     точечные mixin'ы (см. docs/ARCHITECTURE.md §7)
```

## Авторы

* **Разработчик:** Avelc
* **Команда:** PuffSpark

## Лицензия

GNU Lesser General Public License v3.0 (LGPL-3.0). Подробнее в файле [LICENSE](LICENSE).
