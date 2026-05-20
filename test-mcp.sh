#!/usr/bin/env bash
set -euo pipefail

PORT="${MCP_PORT:-64344}"
HOST="${MCP_HOST:-127.0.0.1}"
MCP_URL="http://$HOST:$PORT"
SSE_TIMEOUT="${SSE_TIMEOUT:-15}"
CALL_TIMEOUT="${CALL_TIMEOUT:-30}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] <symbol>

Options:
  -p, --port PORT       MCP server port (default: $PORT, env: MCP_PORT)
  -h, --host HOST       MCP server host (default: $HOST, env: MCP_HOST)
  -P, --project PATH    Project root path (optional, for multi-project setups)
  -l, --list-tools      List all available tools instead of calling find_usages
  -t, --tool NAME       Tool name to call (default: find_usages)
  -a, --args JSON       Tool arguments as JSON (overrides symbol/project)
  --raw                 Print raw JSON response instead of formatted output
  --help                Show this help

Examples:
  $(basename "$0") "\\\\App\\\\Service\\\\EmailService::sendEmail"
  $(basename "$0") -P /path/to/project "\\\\Raketa\\\\Library\\\\BehatContext\\\\Config"
  $(basename "$0") -l
  $(basename "$0") -t get_file_problems -a '{"filePath":"src/Foo.php"}'
EOF
    exit 1
}

SYMBOL=""
PROJECT_PATH=""
LIST_TOOLS=false
TOOL="find_usages"
TOOL_ARGS=""
RAW=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port)   PORT="$2"; shift 2 ;;
        -h|--host)   HOST="$2"; MCP_URL="http://$HOST:$PORT"; shift 2 ;;
        -P|--project) PROJECT_PATH="$2"; shift 2 ;;
        -l|--list-tools) LIST_TOOLS=true; shift ;;
        -t|--tool)   TOOL="$2"; shift 2 ;;
        -a|--args)   TOOL_ARGS="$2"; shift 2 ;;
        --raw)       RAW=true; shift ;;
        --help)      usage ;;
        -*)          echo -e "${RED}Unknown option: $1${NC}"; usage ;;
        *)           SYMBOL="$1"; shift ;;
    esac
done

if [ "$LIST_TOOLS" = false ] && [ -z "$SYMBOL" ] && [ -z "$TOOL_ARGS" ]; then
    echo -e "${RED}Error: symbol or --args required${NC}"
    usage
fi

# Check server is reachable (SSE connections time out, exit code may be 28)
echo -e "${YELLOW}Checking MCP server at $MCP_URL/sse ...${NC}"
HTTP_CODE=$(set +e; curl -s -o /dev/null -w "%{http_code}" "$MCP_URL/sse" --max-time 2 2>/dev/null; true)
if [ "$HTTP_CODE" != "200" ]; then
    echo -e "${RED}Server returned HTTP $HTTP_CODE or is unreachable${NC}"
    exit 1
fi
echo -e "${GREEN}Server is reachable${NC}"

# Start SSE listener in background
SSE_FILE=$(mktemp /tmp/mcp_sse.XXXXXX)
trap "rm -f '$SSE_FILE'" EXIT

timeout "$SSE_TIMEOUT" curl -sN "$MCP_URL/sse" --max-time "$SSE_TIMEOUT" > "$SSE_FILE" 2>&1 &
SSE_PID=$!
sleep 0.5

# Extract session ID
SESSION_ID=$(grep -oP 'sessionId=[a-f0-9-]+' "$SSE_FILE" | head -1 || true)
if [ -z "$SESSION_ID" ]; then
    echo -e "${RED}Failed to get session ID from SSE${NC}"
    kill "$SSE_PID" 2>/dev/null
    exit 1
fi
echo -e "${GREEN}Session: $SESSION_ID${NC}"

# Helper to POST and check for "Accepted"
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
echo -e "${YELLOW}Initializing MCP session...${NC}"
post 1 "initialize" '{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}'
sleep 0.3

# Initialized notification
post 2 "notifications/initialized" '{}'
sleep 0.3

if [ "$LIST_TOOLS" = true ]; then
    echo -e "${YELLOW}Listing tools...${NC}"
    post 3 "tools/list" '{}'
    sleep 2
else
    # Build arguments
    if [ -n "$TOOL_ARGS" ]; then
        ARGS="$TOOL_ARGS"
    else
        ARGS="{\"symbol\":\"$SYMBOL\""
        [ -n "$PROJECT_PATH" ] && ARGS="$ARGS,\"projectPath\":\"$PROJECT_PATH\""
        ARGS="$ARGS}"
    fi

    echo -e "${YELLOW}Calling $TOOL ...${NC}"
    echo -e "${YELLOW}Args: $ARGS${NC}"
    post 10 "tools/call" "{\"name\":\"$TOOL\",\"arguments\":$ARGS}"
    sleep 3
fi

# Kill SSE listener
kill "$SSE_PID" 2>/dev/null
wait "$SSE_PID" 2>/dev/null || true

if [ "$RAW" = true ]; then
    echo ""
    echo -e "${GREEN}=== Raw SSE response ===${NC}"
    cat "$SSE_FILE"
    exit 0
fi

# Parse the last meaningful result
RESULT=$(grep -oP 'data: \{"id":[0-9]+.*' "$SSE_FILE" | tail -1 | sed 's/^data: //' || true)

if [ -z "$RESULT" ]; then
    echo -e "${RED}No result received${NC}"
    cat "$SSE_FILE"
    exit 1
fi

# Pretty print
echo ""
echo -e "${GREEN}=== Result ===${NC}"

if [ "$LIST_TOOLS" = true ]; then
    echo "$RESULT" | python3 -c "
import json, sys
data = json.load(sys.stdin)
tools = data.get('result', {}).get('tools', [])
for t in tools:
    name = t.get('name', '')
    desc = t.get('description', '')[:80]
    print(f'  \033[1;36m{name}\033[0m — {desc}')
print(f'\nTotal: {len(tools)} tools')
" 2>/dev/null || echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT" || true
else
    echo "$RESULT" | python3 -c "
import json, sys
data = json.load(sys.stdin)
content = data.get('result', {}).get('structuredContent', {})
err = content.get('error')
usages = content.get('usages', [])

if err:
    print(f'  \033[0;31mERROR: {err}\033[0m')
elif not usages:
    print('  No usages found')
else:
    print(f'  Found {len(usages)} usage(s):')
    print()
    for u in usages:
        print(f'  \033[1;36m{u[\"file\"]}\033[0m')
        print(f'    Line {u[\"line\"]}:{u[\"column\"]}')
        print(f'    {u[\"lineText\"]}')
        print()
" 2>/dev/null || echo "$RESULT" | python3 -m json.tool 2>/dev/null || echo "$RESULT" || true
fi
