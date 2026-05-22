# PHP Test Project for intellij-php-mcp

Тестовый PHP-проект для проверки MCP-инструментов плагина `intellij-php-mcp`.

## Назначение

Проект содержит各种 PHP-конструкции (классы, интерфейсы, трейты, абстрактные классы) и их использования,
чтобы проверять работу MCP-инструментов в реальной IDE:

- `find_usages` — поиск использований символа
- `find_definition` — переход к определению символа
- `inspect_php_file` — публичный контракт файла
- `get_file_problems` — ошибки и предупреждения в файле

## Сценарии проверки

### Поиск использований метода трейта

```
./test-mcp.sh "\App\Trait\MetricsTrait::getMetrics"
```

Ожидается 4 использования:
- `src/Service/Raketa.php` — `$this->getMetrics()`
- `src/Service/CoreConfigurator.php` — `$this->getMetrics()`
- `public/index.php` — `$raketa->getMetrics()`
- `public/index.php` — `$configurator->getMetrics()`

### Поиск использования метода трейта (LoggableTrait)

```
./test-mcp.sh "\App\Trait\LoggableTrait::getLogs"
```

Ожидается 2 использования:
- `src/Service/UserService.php` — `$this->log()` (там нет, это getLogs вызывается извне)
- `public/index.php` — `$userService->getLogs()`

### Поиск использования класса

```
./test-mcp.sh "\App\Service\EmailService"
```

### Определение символа

```
./test-mcp.sh -t find_definition "\App\Trait\MetricsTrait::getMetrics"
./test-mcp.sh -t find_definition "\App\Service\Raketa"
```

### Проблемы файла

```
./test-mcp.sh -t get_file_problems -a '{"filePath":"src/Service/Raketa.php"}'
```

### Инспекция публичного контракта

```
./test-mcp.sh -t inspect_php_file -a '{"filePath":"src/Service/EmailService.php"}'
```

## Структура

```
test-project/
  public/
    index.php              # Точка входа, вызывает все сервисы
  src/
    Contract/
      ServiceInterface.php # Интерфейс с execute() и getName()
    Trait/
      LoggableTrait.php    # Трейт с log() и getLogs()
      MetricsTrait.php     # Трейт с getMetrics() и resetMetrics()
    Service/
      AbstractService.php  # Абстрактный класс
      EmailService.php     # extends AbstractService implements ServiceInterface
      UserService.php      # implements ServiceInterface, use LoggableTrait
      Raketa.php           # use MetricsTrait
      CoreConfigurator.php # use MetricsTrait
    Model/
      Order.php
      User.php
```
