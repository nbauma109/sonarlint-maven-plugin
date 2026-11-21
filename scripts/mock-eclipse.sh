#!/usr/bin/env bash
set -euo pipefail

report=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --report)
            report="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done
if [ -z "$report" ]; then
    echo "Missing --report argument" >&2
    exit 1
fi

cat > "$report" <<'JSON'
{"formatVersion":1,"issues":[{"file":"src/main/java/example/PriorityViolations.java","line":3,"column":1,"cleanup":"UnnecessaryCodeCleanUp","message":"Remove unused import","startOffset":18,"endOffset":40},{"file":"src/main/java/example/PriorityViolations.java","line":7,"column":5,"cleanup":"PullUpAssignmentCleanUp","message":"Pull up assignment","startOffset":87,"endOffset":87}]}
JSON
