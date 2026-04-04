---
name: openspec-archive-change
description: Архивировать завершённый change после вливания в основную спецификацию. Перемещает директорию change в archive/ с датой.
license: MIT
metadata:
  author: openspec-distillate
  version: "3.0"
---

Архивировать завершённый change.

**Input**: Опционально — имя change. Если не указано, предложить выбор из активных.

**Steps**

1. **Если имя не указано — предложить выбор**

   Найди активные changes:
   ```bash
   ls openspec/changes/ 2>/dev/null
   ```
   Исключи `archive/` из списка. Используй **AskUserQuestion tool** для выбора.

   Показывай только активные (не архивированные) changes.

   **ВАЖНО**: НЕ угадывай и НЕ выбирай автоматически. Пусть пользователь выберет.

2. **Проверь наличие change.md**

   Проверь, что файл `openspec/changes/<name>/change.md` существует.

   **Если change.md не существует:**
   - Покажи предупреждение: change.md не создан
   - Спроси подтверждение через **AskUserQuestion tool**
   - Продолжи при подтверждении

3. **Проверь статус change.md**

   Прочитай `openspec/changes/<name>/change.md` и проверь поле "Статус" в шапке:
   - Если "Реализовано" — всё ок, готов к архивации
   - Если другой статус — предупреди и спроси подтверждение

4. **Выполни архивацию**

   ```bash
   mkdir -p openspec/changes/archive
   ```

   Имя архива: `YYYY-MM-DD-<change-name>`

   **Проверь, что целевая директория не существует:**
   - Если существует — ошибка, предложи переименовать
   - Если нет — перемести:

   ```bash
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```

5. **Покажи результат**

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** analyst-driven
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/

Все артефакты завершены. Change архивирован.
```

**Output With Warnings**

```
## Archive Complete (с предупреждениями)

**Change:** <change-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/

**Предупреждения:**
- change.md не в статусе "Реализовано"
- N невыполненных задач

Проверьте архив, если это не было намеренным.
```

**Guardrails**
- Всегда предлагай выбор change, если имя не указано
- Проверяй статус по содержимому change.md, а не через CLI
- НЕ блокируй архивацию при предупреждениях — информируй и подтверждай
- Вся директория change перемещается целиком
- Покажи понятный итог
