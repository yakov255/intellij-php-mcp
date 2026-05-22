# intellij-php-mcp

MCP (Model Context Protocol) server plugin for IntelliJ‑based IDEs that exposes PHP code analysis tools to AI agents (Claude Code, GitHub Copilot, Cursor, Junie, etc.).

## Tools overview

| Tool | Description |
|---|---|---|
| `find_usages` | Finds all usages of a PHP symbol — class, method (`::method`), field (`::$field`), interface, trait |
| `find_definition` | Finds the declaration location — file, line, column, source line |
| `find_implementations` | Finds all classes that implement an interface or extend a class |
| `inspect_php_file` | Returns the public API contract of a PHP file (bodies stripped, non-public removed) |

---

## `find_usages`

Finds all references to a PHP symbol in the project.

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `symbol` | `string` (required) | The symbol to search for. FQCN (`\App\Service\EmailService`), member (`\App\Service\EmailService::send`), short name (`EmailService`) |
| `projectPath` | `string?` | Absolute path to the project root. Required when multiple projects are open |

### Symbol resolution logic

| Input format | Resolution strategy |
|---|---|
| `\Namespace\Class::method` | Look up `\Namespace\Class` directly. If not found → not found |
| `ShortName::method` | `getClassesByName(ShortName)`. 0 matches → not found, 1 → resolved, 2+ → ambiguous error with all FQCNs listed |
| `\Namespace\Class` | `getClassesByFQN`, fallback `getInterfacesByFQN`, fallback `getTraitsByFQN` |
| `ShortName` | Same as above, but by short name with ambiguity resolution |
| `::method` (leading `::`) | Treated as `ShortName::method` after trimming the leading backslash |

### Edge cases

- **Class, interface, trait** are all searched via three fallbacks: `getClassesByFQN` → `getInterfacesByFQN` → `getTraitsByFQN`
- **Empty result** (symbol exists but has no usages) returns `{ usages: [] }` with no error
- **Symbol not found** returns `{ error: "Symbol '...' not found in the project.", usages: [] }`
- **Ambiguous short name** returns `{ error: "Symbol '...' is ambiguous. ... Possible matches:\n...", usages: [] }`
- **Fields** use `::$fieldName` syntax (the `$` is stripped internally)

### Example

```
┃ Who uses \App\Trait\MetricsTrait::getMetrics?
┃
   ⚙ find_usages [symbol=\App\Trait\MetricsTrait::getMetrics]

   4 usages found:

   src/Service/Raketa.php:25       $this->getMetrics()
   src/Service/CoreConfigurator.php:18  $this->getMetrics()
   public/index.php:31             $raketa->getMetrics()
   public/index.php:35             $configurator->getMetrics()

┃ Find usages of Logger
┃
   ⚙ find_usages [symbol=Logger]

   Symbol 'Logger' is ambiguous. Possible matches:
   \App\Service\Logger
   \Monolog\Logger
```

---

## `find_definition`

Finds where a PHP symbol is declared.

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `symbol` | `string` (required) | Same format as `find_usages` |
| `projectPath` | `string?` | Absolute path to the project root |

### Edge cases

- **Member definition** (`Class::method`, `Class::$field`) searches methods first, then fields
- **Traits**: definition of a trait method is found via the trait file, not the class that uses it
- **Not found** returns `{ error: "Definition not found for '...'." }` (all other fields null)
- **Ambiguous short name** returns same ambiguity error as `find_usages`

### Example

```
┃ Where is \App\Service\EmailService::send defined?
┃
   ⚙ find_definition [symbol=\App\Service\EmailService::send]

   File:   src/Service/EmailService.php
   Line:   45
   Column: 5
   Source: public function send(string $to, string $subject): bool

┃ Where is EmailService defined?
┃
   ⚙ find_definition [symbol=EmailService]

   File:   src/Service/EmailService.php
   Line:   10
   Column: 1
   Source: class EmailService extends AbstractService implements ServiceInterface
```

---

## `find_implementations`

Finds all classes that directly implement an interface or extend a class.

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `symbol` | `string` (required) | FQCN of interface or class (`\App\Contract\ServiceInterface`), or short name (`ServiceInterface`) with ambiguity resolution |
| `projectPath` | `string?` | Absolute path to the project root. Required when multiple projects are open |

### Edge cases

- **Class** is also supported — returns direct subclasses via `getDirectSubclasses()`
- **Interface** — returns all classes that directly implement it
- **Not found** returns `{ error: "Symbol '...' not found in the project.", implementations: [] }`
- **Ambiguous short name** returns ambiguity error with all FQCNs listed
- **No implementations** returns `{ implementations: [] }` with no error

### Example

```
┃ Which classes implement \App\Contract\ServiceInterface?
┃
   ⚙ find_implementations [symbol=\App\Contract\ServiceInterface]

   2 implementations found:

   src/Service/EmailService.php:7       class EmailService extends AbstractService implements ServiceInterface
   src/Service/UserService.php:8        class UserService implements ServiceInterface
```

---

## `inspect_php_file`

Returns the public API contract of a PHP file. Non-public members are stripped, method bodies are replaced with semicolons.

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `filePath` | `string` (required) | Path relative to the project root (e.g. `src/Service/EmailService.php`) |
| `projectPath` | `string?` | Absolute path to the project root |

### What is kept vs removed

| Element | Status |
|---|---|
| `namespace`, `use` declarations | Kept as-is |
| Public methods | Kept, body replaced with `;` |
| Public fields (`public $foo`) | Kept as-is |
| Public constants | Kept as-is |
| Non-public methods (`protected`/`private`) | Removed entirely |
| Non-public fields | Removed entirely |
| Method bodies | Replaced with `;` |

### Edge cases

- **File not found** returns `{ error: "File not found: src/..." }`
- **File with no PHP classes** — raw file text is returned unchanged
- **Multiple classes in one file** — all are processed independently
- **Resolves file** against content source roots first, falls back to resolving relative to `project.basePath`
- **Method body detection** looks for `{` token in children, then `GroupStatement`

### Example

```
┃ Show me the public API of RocketLauncher.php
┃
   ⚙ inspect_php_file [filePath=src/Service/RocketLauncher.php]

   namespace App\Service;

   use App\Contract\Launchable;

   class RocketLauncher implements Launchable {
       public function __construct(string $name): void
       public function launch(): bool
       public function getName(): string
   }

┃ Inspect a file with no classes
┃
   ⚙ inspect_php_file [filePath=src/functions.php]

   <?php

   function helper(): string {
       return 'hello';
   }
```

---

## Requirements

- IntelliJ IDEA Ultimate (2025.2+) или PhpStorm
- PHP plugin enabled
- MCP Server plugin (bundled with IntelliJ IDEA, included automatically)

## Installation

Download the latest plugin JAR from [GitHub Releases](https://github.com/yakov255/intellij-php-mcp/releases).

Install it manually in the IDE: <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>

## Configuration

1. Install the plugin and restart your IDE.
2. Go to <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>MCP Server</kbd>.
3. Enable the MCP Server and set a port (default: 64344).
4. Connect your MCP client (Claude Code, etc.) to `http://localhost:<port>/sse` or `http://localhost:<port>/mcp`.

