#!/usr/bin/env bash
set -euo pipefail

report=""
project=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --report)
            report="$2"
            shift 2
            ;;
        --source|--profile|--source-root|--classpath|-data|-application)
            shift 2
            ;;
        -nosplash|-consoleLog|-clean)
            shift
            ;;
        *)
            project="$1"
            shift
            ;;
    esac
done
if [ -z "$report" ] || [ -z "$project" ]; then
    echo "Missing project or --report argument" >&2
    exit 1
fi

python3 - "$project" "$report" <<'PY'
import json
from pathlib import Path
import sys

project = Path(sys.argv[1])
destination = Path(sys.argv[2])
source_path = "src/main/java/example/PriorityViolations.java"
source = (project / source_path).read_text(encoding="utf-8")

def issue(needle, cleanup, message, occurrence=0):
    position = -1
    for _ in range(occurrence + 1):
        position = source.index(needle, position + 1)
    line = source.count("\n", 0, position) + 1
    start = source.rfind("\n", 0, position) + 1
    return {
        "file": source_path,
        "line": line,
        "column": position - start + 1,
        "cleanup": cleanup,
        "module": "MockCleanupModule",
        "message": message,
        "startOffset": position,
        "endOffset": position + len(needle),
    }

issues = [
    issue("import java.util.List;", "cleanup.remove_unused_imports", "Remove unused import"),
    issue("int unused = 1;", "cleanup.remove_unused_local_variables", "Remove unused local variable"),
    issue("(String)", "cleanup.remove_unnecessary_casts", "Remove unnecessary cast"),
    issue("return", "cleanup.remove_unnecessary_casts", "Unrelated edit on the cast line",
          occurrence=1),
    issue("public void BadName()", "cleanup.pull_up_assignment", "Unrelated cleanup"),
]
destination.write_text(json.dumps({"formatVersion": 1, "issues": issues}), encoding="utf-8")
PY
