# Project Context for AI Agents

## intellij-php-mcp

MCP-сервер плагин для IntelliJ IDEA Ultimate / PhpStorm, который через протокол MCP (Model Context Protocol) предоставляет AI-агентам инструменты анализа PHP-кода: поиск использований, определение символов, поиск реализаций интерфейсов, инспекцию контрактов и анализ ошибок.

## Архитектура

IntelliJ Platform Plugin на Kotlin. Gradle-сборка.

```
src/main/kotlin/com/github/yakov255/intellijphpmcp/mcp/
├── PhpToolset.kt                  # Точка входа MCP — @McpTool методы + результат DTO
├── PhpFindUsagesService.kt        # Сервис: разрешение символов, поиск usages/definition/implementations
└── PhpContractInspectorService.kt # Сервис: инспекция публичного контракта PHP-файла
```

Связка: плагин регистрирует `McpToolset` (PhpToolset) в `plugin.xml`. MCP-сервер (com.intellij.mcpServer) принимает JSON-RPC вызовы и диспатчит их в инструменты. Каждый инструмент разрешает проект через `projectPath`, получает сервис и делегирует логику.

## Доступные MCP-инструменты

| Инструмент | Описание | Сервис |
|---|---|---|
| `find_usages` | Поиск всех использований символа (класс, метод `::method`, поле `::$field`, интерфейс, трейт) | `PhpFindUsagesService` |
| `find_definition` | Поиск объявления символа (файл, строка, колонка, текст строки) | `PhpFindUsagesService` |
| `find_implementations` | Поиск классов, реализующих интерфейс или наследующих класс | `PhpFindUsagesService` |
| `inspect_php_file` | Публичный контракт PHP-файла (только public, тела заменены на `;`) | `PhpContractInspectorService` |

Дополнительные инструменты (из состава `com.intellij.mcpServer`): `get_file_problems`, `get_project_dependencies`, `get_project_modules`, `rename_refactoring`, `search_in_files_by_regex` и др.

## Символ-резолюция (PhpFindUsagesService)

Входная строка `symbol` проходит через `resolveSymbol()`:

| Формат | Алгоритм |
|---|---|
| `\Namespace\Class::method` | Прямой поиск `\Namespace\Class`. Если не найден → NotFound |
| `ShortName::method` | `getClassesByName(ShortName)`. 0 → NotFound, 1 → Resolved, 2+ → Ambiguous |
| `\Namespace\Class` | `getClassesByFQN` → `getInterfacesByFQN` → `getTraitsByFQN` (три fallback) |
| `ShortName` | То же, но по короткому имени с резолвингом ambiguous |
| `::method` | Ведущий `::` обрезается, обрабатывается как `ShortName::method` |

Три fallback в `classExists()`: `getClassesByFQN` → `getInterfacesByFQN` → `getTraitsByFQN`. Используется везде: resolveSymbol, findUsages, findDefinition, findImplementations.

## Структура test-project/

```
test-project/
├── public/index.php          # Точка входа — использует все классы
└── src/
    ├── Contract/
    │   └── ServiceInterface.php   # interface ServiceInterface { execute(), getName() }
    ├── Trait/
    │   ├── LoggableTrait.php      # trait { log(), getLogs() }
    │   └── MetricsTrait.php       # trait { getMetrics(), resetMetrics() }
    ├── Service/
    │   ├── AbstractService.php    # abstract class AbstractService { execute(), validate(), sanitize() }
    │   ├── EmailService.php       # extends AbstractService implements ServiceInterface
    │   ├── UserService.php        # implements ServiceInterface, use LoggableTrait
    │   ├── Raketa.php             # use MetricsTrait
    │   └── CoreConfigurator.php   # use MetricsTrait
    └── Model/
        ├── Order.php              # process(ServiceInterface $processor)
        └── User.php               # простой model-класс
```

### Ключевые связи:

- **ServiceInterface** — реализован в `EmailService`, `UserService`. Используется в `Order::process()`
- **MetricsTrait** — используется в `Raketa`, `CoreConfigurator`. Вызывается из `index.php`
- **LoggableTrait** — используется в `UserService`. Вызывается из `index.php`
- **AbstractService** — родитель для `EmailService`. Содержит protected `validate()` и private `sanitize()`

## test-mcp.sh

Скрипт для ручного тестирования MCP-инструментов. Требует запущенный MCP-сервер.

```bash
./test-mcp.sh <symbol>                           # find_usages (по умолчанию)
./test-mcp.sh -t find_definition <symbol>
./test-mcp.sh -t find_implementations <symbol>
./test-mcp.sh -t get_file_problems -a '{"filePath":"..."}'
./test-mcp.sh -t inspect_php_file -a '{"filePath":"..."}'
./test-mcp.sh list                                # список всех инструментов
```

- Порт по умолчанию: 64344 (MCP_PORT)
- Всегда передаёт `projectPath` → `test-project/`
- После исправления: символы с бэкслешами (`\App\...`) корректно экранируются в JSON

## run-tests.sh

Полный автоматический тест-раннер. 18 тестов в 6 группах:

| # | Группа | Тестов | Что проверяет |
|---|---|---|---|
| 1 | find_usages | 6 | Трейт-метод (2), класс (1), интерфейс (1), несуществующий (2) |
| 2 | find_definition | 4 | Трейт-метод, класс, интерфейс, несуществующий |
| 3 | find_implementations | 2 | ServiceInterface (EmailService + UserService), несуществующий |
| 4 | get_file_problems | 2 | Нормальный файл, несуществующий файл |
| 5 | inspect_php_file | 3 | Класс-контракт, трейт-контракт, несуществующий файл |
| 6 | tools/list | 1 | Все инструменты в списке |

```bash
# Перед запуском: убедись, что MCP-сервер запущен
./run-tests.sh
```

## Известные особенности

1. **Трейты не находятся через `getClassesByFQN`**. Для трейтов используется fallback `getTraitsByFQN`. Без него поиск usages/definition методов трейта возвращает пустой результат. Фикс в `findMemberUsages`, `findMemberDefinition`, `findImplementations` и везде, где идёт поиск класса через FQCN.

2. **PSR-4 автозагрузка**: `App\` → `src/`. Пути в результатах всегда относительные (от `project.basePath`).

3. **Символ-резолюция не умеет разрешать методы по `getMethodsByName`**. Методы ищутся только через parent class → `methods` → фильтр по `name`. Если метод не является непосредственным членом класса (например, унаследован), он не будет найден при синтаксисе `::method`.

4. **find_implementations использует `getDirectSubclasses()`** — возвращает только прямых наследников/реализаторов. Для транзитивных используйте `getAllSubclasses()`.

5. **ESCAPING**: При вызове `test-mcp.sh` символы с бэкслешами (`\App\...`) корректно экранируются скриптом для JSON. Раньше `\A`, `\S` и т.д. давали invalid JSON (HTTP 400).

## Сборка

```bash
./gradlew buildPlugin       # собрать .zip в build/distributions/
./gradlew test              # unit-тесты (Mockito, индексы не нужны)
./gradlew compileKotlin     # только компиляция
```

JAR-файл устанавливается в IDE через Settings → Plugins → ⚙ → Install plugin from disk.
После переустановки требуется перезапуск IDE.
