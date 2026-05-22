#!/usr/bin/env bash
set -euo pipefail

MCP_SH="$(cd "$(dirname "$0")" && pwd)/test-mcp.sh"
PASS=0
FAIL=0
TOTAL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
NC='\033[0m'

cleanup() {
    rm -f /tmp/mcp_test_*.json /tmp/mcp_test_*.txt
}
trap cleanup EXIT

# ── Helpers ──────────────────────────────────────────────────────────────────

call_tool() {
    local out_file
    out_file=$(mktemp /tmp/mcp_test_XXXXXX.json)
    "$MCP_SH" --raw "$@" > "$out_file" 2>/dev/null || true
    echo "$out_file"
}

extract_json() {
    local file="$1"
    grep -oP 'data: \{"id":[0-9]+.*' "$file" | tail -1 | sed 's/^data: //' || echo ""
}

assert_json() {
    local json="$1" py_expr="$2"
    local tmp_json
    tmp_json=$(mktemp /tmp/mcp_test_XXXXXX.json)
    echo "$json" > "$tmp_json"
    python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
exec(sys.argv[2])
" "$tmp_json" "$py_expr" 2>&1
    local rc=$?
    rm -f "$tmp_json"
    return $rc
}

# ── Test runner ──────────────────────────────────────────────────────────────

run() {
    local name="$1"
    local py_check="$2"
    shift 2

    TOTAL=$((TOTAL + 1))
    echo -n "  [$TOTAL] $name ... "

    local out_file json
    out_file=$(call_tool "$@")
    json=$(extract_json "$out_file")
    rm -f "$out_file"

    if [ -z "$json" ]; then
        echo -e "${RED}FAIL${NC} (no response)"
        FAIL=$((FAIL + 1))
        return
    fi

    if assert_json "$json" "$py_check"; then
        echo -e "${GREEN}PASS${NC}"
        PASS=$((PASS + 1))
    else
        local reason
        reason=$(echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
r = d.get('result', {})
s = r.get('structuredContent', {})
e = s.get('error', '')
print(e or 'assertion failed')
" 2>/dev/null || echo "parse error")
        echo -e "${RED}FAIL${NC} ($reason)"
        FAIL=$((FAIL + 1))
    fi
}

# ── Test suites ──────────────────────────────────────────────────────────────

suite_header() {
    echo ""
    echo -e "${YELLOW}━━━ $1 ━━━${NC}"
}

# ═══════════════════════════════════════════════════════════════════════════════
#  find_usages
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "find_usages"

run "Trait method via class" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) >= 3, f"expected >=3 usages, got {len(usages)}"
files = [u.get("file", "") or u.get("relativePath", "") for u in usages]
assert any("Raketa" in f for f in files), f"Raketa not in {files}"
assert any("CoreConfigurator" in f for f in files), f"CoreConfigurator not in {files}"
' '\App\Trait\MetricsTrait::getMetrics'

run "Trait method via class (LoggableTrait)" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) >= 1, f"expected >=1 usages, got {len(usages)}"
files = [u.get("file", "") or u.get("relativePath", "") for u in usages]
assert any("index.php" in f for f in files), f"index.php not in {files}"
' '\App\Trait\LoggableTrait::getLogs'

run "Class" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) >= 2, f"expected >=2 usages, got {len(usages)}"
files = [u.get("file", "") or u.get("relativePath", "") for u in usages]
assert any("Order.php" in f for f in files), f"Order.php not in {files}"
' '\App\Service\EmailService'

run "Interface" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) >= 3, f"expected >=3 usages, got {len(usages)}"
files = [u.get("file", "") or u.get("relativePath", "") for u in usages]
assert any("EmailService" in f for f in files), f"EmailService not in {files}"
assert any("UserService" in f for f in files), f"UserService not in {files}"
assert any("Order.php" in f for f in files), f"Order.php not in {files}"
' '\App\Contract\ServiceInterface'

run "Non-existent symbol" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) == 0, f"expected 0 usages, got {len(usages)}"
' '\App\NoSuchClass'

run "Non-existent trait method" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) == 0, f"expected 0 usages, got {len(usages)}"
' '\App\Trait\MetricsTrait::noSuchMethod'

# ═══════════════════════════════════════════════════════════════════════════════
#  find_definition
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "find_definition"

run "Trait method definition" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
file = sc.get("file", "")
assert file != "", "expected a definition file"
assert "MetricsTrait.php" in file, f"expected MetricsTrait.php, got {file}"
' -t find_definition '\App\Trait\MetricsTrait::getMetrics'

run "Class definition" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
file = sc.get("file", "")
assert file != "", "expected a definition file"
assert "EmailService.php" in file, f"expected EmailService.php, got {file}"
' -t find_definition '\App\Service\EmailService'

run "Interface definition" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
file = sc.get("file", "")
assert file != "", "expected a definition file"
assert "ServiceInterface.php" in file, f"expected ServiceInterface.php, got {file}"
' -t find_definition '\App\Contract\ServiceInterface'

run "Non-existent definition" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
usages = sc.get("usages", [])
assert len(usages) == 0, f"expected 0 definitions, got {len(usages)}"
' -t find_definition '\App\NoSuchClass'

# ═══════════════════════════════════════════════════════════════════════════════
#  find_implementations
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "find_implementations"

run "Interface implementations" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
implementations = sc.get("implementations", [])
assert len(implementations) >= 2, f"expected >=2 implementations, got {len(implementations)}"
fqcns = [u.get("fqcn", "") for u in implementations]
files = [u.get("file", "") or u.get("relativePath", "") for u in implementations]
assert any("EmailService" in f for f in files), f"EmailService not in {files}"
assert any("UserService" in f for f in files), f"UserService not in {files}"
' -t find_implementations '\App\Contract\ServiceInterface'

run "Non-existent implementations" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
implementations = sc.get("implementations", [])
assert len(implementations) == 0, f"expected 0 implementations, got {len(implementations)}"
' -t find_implementations '\App\NoSuchClass'

# ═══════════════════════════════════════════════════════════════════════════════
#  get_file_problems
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "get_file_problems"

run "File with no problems" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
problems = sc.get("problems", [])
' -t get_file_problems -a '{"filePath":"src/Service/Raketa.php"}'

run "Non-existent file" '
result = data.get("result", {})
is_error = result.get("isError", False)
assert is_error, "expected error for non-existent file"
' -t get_file_problems -a '{"filePath":"src/NoSuchFile.php"}'

# ═══════════════════════════════════════════════════════════════════════════════
#  inspect_php_file
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "inspect_php_file"

run "Class contract" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
contract = sc.get("contractText", "")
assert "function execute" in contract, f"missing execute in contract"
assert "function getName" in contract, f"missing getName in contract"
' -t inspect_php_file -a '{"filePath":"src/Service/EmailService.php"}'

run "Trait contract" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
assert "error" not in sc, "error: {}".format(sc.get("error"))
contract = sc.get("contractText", "")
assert "function getMetrics" in contract, f"missing getMetrics in contract"
' -t inspect_php_file -a '{"filePath":"src/Trait/MetricsTrait.php"}'

run "Non-existent file for inspect" '
result = data.get("result", {})
sc = result.get("structuredContent", {})
error = sc.get("error", "")
assert error != "", "expected error for non-existent file"
' -t inspect_php_file -a '{"filePath":"src/NoSuchFile.php"}'

# ═══════════════════════════════════════════════════════════════════════════════
#  tools/list
# ═══════════════════════════════════════════════════════════════════════════════

suite_header "tools/list"

run "List all tools" '
result = data.get("result", {})
tools = result.get("tools", [])
names = [t.get("name", "") for t in tools]
assert len(tools) >= 5, f"expected >=5 tools, got {len(tools)}"
assert "find_usages" in names, f"find_usages not in {names}"
assert "find_definition" in names, f"find_definition not in {names}"
assert "find_implementations" in names, f"find_implementations not in {names}"
assert "get_file_problems" in names, f"get_file_problems not in {names}"
assert "inspect_php_file" in names, f"inspect_php_file not in {names}"
' list

# ═══════════════════════════════════════════════════════════════════════════════
#  Summary
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${YELLOW}════════════════════════════════════════════${NC}"
echo -e "  Total:  $TOTAL"
echo -e "  Pass:   ${GREEN}$PASS${NC}"
echo -e "  Fail:   ${RED}$FAIL${NC}"
echo -e "${YELLOW}════════════════════════════════════════════${NC}"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
