#!/usr/bin/env bash
set -euo pipefail

PORT="${MCP_PORT:-64344}"
HOST="${MCP_HOST:-127.0.0.1}"
MCP_URL="http://$HOST:$PORT"
SSE_TIMEOUT="${SSE_TIMEOUT:-15}"
PROJECT_DIR="$(cd "$(dirname "$0")/test-project" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
NC='\033[0m'

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] <symbol-or-command>

Tests MCP tools against the local test project ($PROJECT_DIR).

Commands:
  <symbol>              Find usages of a symbol (default tool)
  list                  List all available MCP tools

Options:
  -p, --port PORT       MCP server port (default: $PORT, env: MCP_PORT)
  -h, --host HOST       MCP server host (default: $HOST, env: MCP_HOST)
  -t, --tool NAME       Tool to call (default: find_usages)
  -a, --args JSON       Raw tool arguments as JSON
  --raw                 Print raw server response
  --help                Show this help

Examples:
  $(basename "$0") "\\\\App\\\\Trait\\\\MetricsTrait::getMetrics"
  $(basename "$0") -t find_definition "\\\\App\\\\Trait\\\\MetricsTrait::getMetrics"
  $(basename "$0") -t get_file_problems -a '{"filePath":"src/Service/Raketa.php"}'
  $(basename "$0") -t inspect_php_file -a '{"filePath":"src/Service/Raketa.php"}'
  $(basename "$0") list
EOF
    exit 1
}

SYMBOL=""
TOOL="find_usages"
TOOL_ARGS=""
RAW=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port)   PORT="$2"; shift 2 ;;
        -h|--host)   HOST="$2"; MCP_URL="http://$HOST:$PORT"; shift 2 ;;
        -t|--tool)   TOOL="$2"; shift 2 ;;
        -a|--args)   TOOL_ARGS="$2"; shift 2 ;;
        --raw)       RAW=true; shift ;;
        --help)      usage ;;
        -*)          echo -e "${RED}Unknown option: $1${NC}"; usage ;;
        list)        SYMBOL="list"; TOOL="tools/list"; shift ;;
        *)           SYMBOL="$1"; shift ;;
    esac
done

if [ -z "$SYMBOL" ] && [ -z "$TOOL_ARGS" ]; then
    echo -e "${RED}Error: symbol or --args required${NC}"
    usage
fi

# Check server
echo -e "${YELLOW}Checking MCP server at $MCP_URL/sse ...${NC}"
HTTP_CODE=$(set +e; curl -s -o /dev/null -w "%{http_code}" "$MCP_URL/sse" --max-time 2 2>/dev/null; true)
if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}Server returned HTTP $HTTP_CODE or is unreachable${NC}"
    exit 1
fi
echo -e "${GREEN}Server OK${NC}"

# SSE session
SSE_FILE=$(mktemp /tmp/mcp_sse.XXXXXX)
trap "rm -f '$SSE_FILE'" EXIT

timeout "$SSE_TIMEOUT" curl -sN "$MCP_URL/sse" --max-time "$SSE_TIMEOUT" > "$SSE_FILE" 2>&1 &
SSE_PID=$!
sleep 0.5

SESSION_ID=$(grep -oP 'sessionId=[a-f0-9-]+' "$SSE_FILE" | head -1 || true)
if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}Failed to get session ID${NC}"
    kill "$SSE_PID" 2>/dev/null
    exit 1
fi
echo -e "${GREEN}Session: $SESSION_ID${NC}"

# POST helper
post() {
    local id="$1" method="$2" params="$3"
    local resp
    resp=$(curl -s -w "\n%{http_code}" "http://$HOST:$PORT/message?$SESSION_ID" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}" \
        --max-time 5 2>/dev/null || true)
    local http_code
    http_code=$(echo "$resp" | tail -1)
    if [ "$http_code" != "200" ] && [ "$http_code" != "202" ]; then
        echo -e "${RED}POST $method failed: HTTP $http_code${NC}"
        return 1
    fi
    return 0
}

# Initialize
echo -e "${YELLOW}Initializing...${NC}"
post 1 "initialize" '{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}'
sleep 0.3
post 2 "notifications/initialized" '{}'
sleep 0.3

# Call tool
if [ "$TOOL" = "tools/list" ]; then
    echo -e "${YELLOW}Listing tools...${NC}"
    post 3 "tools/list" '{}'
    sleep 2
else
    if [ -n "$TOOL_ARGS" ]; then
        ARGS="$TOOL_ARGS"
    else
        ESCAPED_SYMBOL=$(echo "$SYMBOL" | sed 's/\\/\\\\/g')
        ARGS="{\"symbol\":\"$ESCAPED_SYMBOL\",\"projectPath\":\"$PROJECT_DIR\"}"
    fi

    echo -e "${YELLOW}> $TOOL${NC}"
    echo "  project: $PROJECT_DIR"
    echo "  args:    $ARGS"
    post 10 "tools/call" "{\"name\":\"$TOOL\",\"arguments\":$ARGS}"
    sleep 3
fi

# Cleanup
kill "$SSE_PID" 2>/dev/null
wait "$SSE_PID" 2>/dev/null || true

if [ "$RAW" = true ]; then
    echo ""
    echo -e "${GREEN}=== Raw response ===${NC}"
    cat "$SSE_FILE"
    exit 0
fi

# Parse and display
RESULT=$(grep -oP 'data: \{"id":[0-9]+.*' "$SSE_FILE" | tail -1 | sed 's/^data: //' || true)

if [ -z "$RESULT" ]; then
    echo -e "${RED}No result received${NC}"
    cat "$SSE_FILE"
    exit 1
fi

echo ""
echo -e "${GREEN}=== Result ===${NC}"

echo "$RESULT" | python3 -c "
import json, sys
data = json.load(sys.stdin)

if 'result' not in data:
    err = data.get('error', {})
    print(json.dumps(data, indent=2))
    sys.exit(0)

result = data['result']

if 'tools' in result:
    tools = result['tools']
    for t in tools:
        name = t.get('name', '')
        desc = t.get('description', '')[:80]
        print(f'  \033[1;36m{name}\033[0m - {desc}')
    print(f'\nTotal: {len(tools)} tools')
    sys.exit(0)

structured = result.get('structuredContent', {})
err = structured.get('error')

if err:
    print(f'  \033[0;31mERROR: {err}\033[0m')
    sys.exit(0)

# Fallback: show structured content first
if structured:
    if 'error' in structured:
        del structured['error']
    print(f'  structuredContent:')
    print(json.dumps(structured, indent=4))
    print()

# Show usages if present
usages = structured.get('usages', result.get('usages', []))
if usages:
    print(f'  Found {len(usages)} usage(s):')
    print()
    for u in usages:
        print(f'  \033[1;36m{u.get(\"file\", u.get(\"relativePath\", \"?\"))}\033[0m')
        print(f'    Line {u.get(\"line\", \"?\")}:{u.get(\"column\", \"?\")}')
        txt = u.get('lineText', '')
        if txt:
            print(f'    {txt}')
        print()
elif structured:
    pass
elif 'content' in result:
    for item in result['content']:
        if item.get('type') == 'text':
            print(item.get('text', ''))
else:
    print(json.dumps(result, indent=2))
" 2>/dev/null || echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT" || true
