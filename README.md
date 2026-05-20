# intellij-php-mcp

MCP (Model Context Protocol) server plugin for IntelliJ‑based IDEs that exposes PHP code analysis tools to AI agents (Claude Code, GitHub Copilot, Cursor, Junie, etc.).

## Tools

| Tool | Description |
|---|---|
| `find_usages` | Finds all usages of a PHP symbol — class, method (`::methodName`), field (`::$field`). Accepts FQCN or short name (with ambiguity resolution). |
| `inspect_php_file` | Returns the public API contract of a PHP file: namespace, imports, public methods (bodies stripped), public fields. Non‑public members are removed. |

## Requirements

- IntelliJ IDEA Ultimate (2025.2+)
- PHP plugin enabled
- MCP Server plugin (bundled with IntelliJ IDEA, included automatically)

## Installation

### From source

```bash
git clone https://github.com/yakov255/intellij-php-mcp.git
cd intellij-php-mcp
./gradlew build
```

The plugin JAR will be in `build/libs/`. Install it via <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk…</kbd>

### Via IDE (once published)

<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search for "PHP MCP".

## Configuration

1. Install the plugin and restart your IDE.
2. Go to <kbd>Settings</kbd> > <kbd>Tools</kbd> > <kbd>MCP Server</kbd>.
3. Enable the MCP Server and set a port (default: 64344).
4. Connect your MCP client (Claude Code, etc.) to `http://localhost:<port>/sse` or `http://localhost:<port>/mcp`.

## Development

```bash
./gradlew runIde
```

Run the test script against a running IDE:

```bash
./test-mcp.sh "\App\Service\EmailService"
./test-mcp.sh -t inspect_php_file -a '{"filePath":"src/Foo.php"}'
./test-mcp.sh -l
```

## Project structure

```
src/main/kotlin/com/github/yakov255/intellijphpmcp/mcp/
├── PhpToolset.kt                    # MCP tool definitions
├── PhpFindUsagesService.kt          # Symbol resolution + ReferencesSearch
├── PhpContractInspectorService.kt   # Public API contract extraction
```

## License

MIT
