#!/bin/bash
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // ""')

if echo "$command" | grep -qE 'git (commit|push)'; then
  echo "git commit/push는 Claude가 실행할 수 없습니다. 직접 실행해주세요." >&2
  exit 2
fi
