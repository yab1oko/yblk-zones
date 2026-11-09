# CurseForge — поля проекта и загрузка

**GitHub:** https://github.com/yab1oko/yblk-zones  
На CurseForge в Source URL / Issues укажи этот адрес.

Скриншоты для страницы лежат в `docs/screenshots/`:

| Файл | Что показать |
|------|----------------|
| `01_editor.png` | Двухпанельный редактор зоны (флаги + настройки) |
| `02_wand_hud.png` | Жезл и HUD подсказок над зоной |
| `03_polygon_edit.png` | Редактирование полигональной зоны |

На CurseForge: **Files → Upload images** (или вставь URL после загрузки в description).

---

## Categories (CurseForge)

Выбери **primary** + до 2–3 доп. (не больше нужного):

| Приоритет | Category | Почему |
|-----------|----------|--------|
| **1 — Primary** | **Server Utility** | Зоны, claim, ивенты, скрипты на сервере |
| **2** | **Map and Information** | Outline, HUD имени зоны, визуализация границ |
| **3** | **Utility & QoL** | Редактор, шаблоны, удобные инструменты владельца |
| Опционально | **Adventure and RPG** | Рейды / ивенты / скриптовые сценарии |

**Не ставить:** Technology, World Gen, Magic, Storage, Mobs (как основную) — мод не про это.

Рекомендуемый набор на форме Create project:

1. Server Utility *(primary)*  
2. Map and Information  
3. Utility & QoL  

---

## GitHub topics

Уже можно проставить на репо:

`minecraft` `minecraft-mod` `forge` `minecraft-forge` `minecraft-1-20-1` `zones` `mit-license`

---

## Summary

```
Create zones. Lots of configuration options and the ability to write scripts.
```

---

## Description

Сначала загрузи 3 скрина на CF, затем замени `SCREENSHOT_1_URL` / `2` / `3` на прямые ссылки на картинки.

```html
<p><b>YBLK Zones</b> for Minecraft <b>Forge 1.20.1</b>.</p>
<p>Create zones. Lots of configuration options and the ability to write scripts.</p>

<h3>Screenshots</h3>

<p><b>Zone editor</b> — flags and detailed settings</p>
<img src="SCREENSHOT_1_URL" alt="Zone editor" width="100%"/>

<p><b>Zone Wand</b> — draw zones in the world</p>
<img src="SCREENSHOT_2_URL" alt="Zone Wand HUD" width="100%"/>

<p><b>Polygon editing</b> — shape zones freely</p>
<img src="SCREENSHOT_3_URL" alt="Polygon zone edit" width="100%"/>

<h3>Features</h3>
<ul>
  <li>Zone Wand — draw and edit world zones</li>
  <li>Many flags, HUD, ambience, particles, potion effects</li>
  <li>Buyable plots, templates, owner tools</li>
  <li>Events / raids with waves and rewards</li>
  <li>Scripts (on start / end / enter / exit), text editor, save &amp; import</li>
</ul>

<h3>Dependencies</h3>
<ul>
  <li><b>Required:</b> Minecraft 1.20.1</li>
  <li><b>Required:</b> Forge 47.x (recommended 47.4.0+)</li>
  <li><b>Optional:</b> none</li>
</ul>

<p>Install Forge, drop the jar into <code>mods</code>, done.</p>

<hr/>

<p><b>YBLK Zones</b> для Minecraft <b>Forge 1.20.1</b>.</p>
<p>Создавайте зоны. Очень много настроек и возможность писать скрипты.</p>

<h3>Скриншоты</h3>

<p><b>Редактор зоны</b> — флаги и детальные настройки</p>
<img src="SCREENSHOT_1_URL" alt="Редактор зоны" width="100%"/>

<p><b>Жезл зон</b> — рисование зон в мире</p>
<img src="SCREENSHOT_2_URL" alt="Жезл и HUD" width="100%"/>

<p><b>Полигон</b> — свободная форма зоны</p>
<img src="SCREENSHOT_3_URL" alt="Редактирование полигона" width="100%"/>

<h3>Возможности</h3>
<ul>
  <li>Жезл зон — рисование и редактирование</li>
  <li>Флаги, HUD, амбиент, частицы, эффекты</li>
  <li>Продажа участков, шаблоны, меню владельца</li>
  <li>Ивенты / рейды с волнами и наградами</li>
  <li>Скрипты, текстовый редактор, сохранение и импорт между зонами</li>
</ul>

<h3>Зависимости</h3>
<ul>
  <li><b>Обязательно:</b> Minecraft 1.20.1</li>
  <li><b>Обязательно:</b> Forge 47.x (лучше 47.4.0+)</li>
  <li><b>Опционально:</b> нет</li>
</ul>
```

---

## Dependencies (Upload file)

| | |
|---|---|
| Game version | **1.20.1** |
| Mod loader | **Forge** |
| Other mods | none |

---

## Проект на CF

1. **Name:** YBLK Zones · **Slug:** `yblk-zones`  
2. **License:** MIT  
3. **Icon:** `src/main/resources/logo.png`  
4. Загрузить скрины из `docs/screenshots/`  
5. Вставить Description (с URL картинок)  
6. **File:** `build/libs/yblk_zones-1.20.1-1.0.0.jar`
