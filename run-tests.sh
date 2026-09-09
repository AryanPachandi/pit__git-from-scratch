#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/codecrafters-git-build.XXXXXX")
CASE_DIR=$(mktemp -d "${TMPDIR:-/tmp}/codecrafters-git-tests.XXXXXX")
JAR="$BUILD_DIR/codecrafters-git.jar"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

cleanup() {
  rm -rf "$BUILD_DIR" "$CASE_DIR"
}
trap cleanup EXIT

record() {
  local result=$1
  local name=$2
  local detail=${3:-}
  case "$result" in
    PASS) PASS_COUNT=$((PASS_COUNT + 1)); printf 'PASS: %s\n' "$name" ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT + 1)); printf 'FAIL: %s%s\n' "$name" "${detail:+ - $detail}" ;;
    SKIP) SKIP_COUNT=$((SKIP_COUNT + 1)); printf 'SKIP: %s%s\n' "$name" "${detail:+ - $detail}" ;;
  esac
}

assert_file() {
  [[ -f "$1" ]] || { echo "missing file: $1"; return 1; }
}

assert_dir() {
  [[ -d "$1" ]] || { echo "missing directory: $1"; return 1; }
}

run_case() {
  local name=$1
  shift
  local case_dir="$CASE_DIR/${name//[^A-Za-z0-9_-]/_}"
  mkdir -p "$case_dir"
  if "$@" "$case_dir"; then
    record PASS "$name"
  else
    local message
    message=$(cat "$case_dir/error" 2>/dev/null || printf 'assertion failed')
    record FAIL "$name" "$message"
  fi
}

case_init() {
  local dir=$1
  local output
  output=$(cd "$dir" && "${JAR_CMD[@]}" init 2>"$dir/stderr") || { echo "init exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$output" == *"Initialized git directory"* ]] || { echo "unexpected init output: $output" > "$dir/error"; return 1; }
  assert_dir "$dir/.git/objects" || return 1
  assert_dir "$dir/.git/refs" || return 1
  assert_dir "$dir/.git/refs/heads" || return 1
  assert_file "$dir/.git/HEAD" || return 1
  [[ $(cat "$dir/.git/HEAD") == "ref: refs/heads/main" ]] || { echo "HEAD does not point to main" > "$dir/error"; return 1; }
  return 0
}

case_hash_and_cat_file() {
  local dir=$1
  printf 'hello from a blob\n' > "$dir/input.txt"
  local expected hash actual
  expected=$(python3 - "$dir/input.txt" <<'PY'
import hashlib
from pathlib import Path
p = Path(__import__('sys').argv[1])
data = p.read_bytes()
print(hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest())
PY
)
  hash=$(cd "$dir" && "${JAR_CMD[@]}" hash-object input.txt 2>"$dir/stderr") || { echo "hash-object exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$hash" == "$expected" ]] || { echo "expected hash $expected, got $hash" > "$dir/error"; return 1; }
  assert_file "$dir/.git/objects/${hash:0:2}/${hash:2}" || return 1
  actual=$(cd "$dir" && "${JAR_CMD[@]}" cat-file -p "$hash" 2>>"$dir/stderr") || { echo "cat-file exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$actual" == "hello from a blob" ]] || { echo "cat-file returned: $actual" > "$dir/error"; return 1; }
  return 0
}

case_hash_edge_cases() {
  local dir=$1
  : > "$dir/empty"
  local hash
  hash=$(cd "$dir" && "${JAR_CMD[@]}" hash-object empty 2>"$dir/stderr") || { echo "empty hash-object exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$hash" == "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391" ]] || { echo "wrong empty blob hash: $hash" > "$dir/error"; return 1; }
  return 0
}

read_index() {
  local index=$1
  python3 - "$index" <<'PY'
import struct
import sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()
if data[:8] != b'PITINDEX':
    raise SystemExit('unexpected index magic')
version, count = struct.unpack_from('>II', data, 8)
offset = 16
entries = []
for _ in range(count):
    path_length = struct.unpack_from('>I', data, offset)[0]
    offset += 4
    path = data[offset:offset + path_length].decode()
    offset += path_length
    hash_length = struct.unpack_from('>I', data, offset)[0]
    offset += 4
    object_hash = data[offset:offset + hash_length].decode()
    offset += hash_length
    entries.append((path, object_hash))
for path, object_hash in entries:
    print(f'{path} {object_hash}')
PY
}

case_add() {
  local dir=$1
  local expected updated index_before index_after
  printf 'first version\n' > "$dir/README.md"
  printf 'alpha\n' > "$dir/a.txt"
  printf 'beta\n' > "$dir/b.txt"
  : > "$dir/empty.txt"
  mkdir "$dir/nested"
  printf 'nested\n' > "$dir/nested/file.txt"
  python3 - "$dir/binary.dat" <<'PY'
from pathlib import Path
import sys
Path(sys.argv[1]).write_bytes(bytes([0, 1, 127, 128, 255]))
PY
  (cd "$dir" && "${JAR_CMD[@]}" init > /dev/null 2>"$dir/stderr") || { echo "init failed" > "$dir/error"; return 1; }

  expected=$(python3 - "$dir/README.md" <<'PY'
import hashlib
from pathlib import Path
import sys
data = Path(sys.argv[1]).read_bytes()
print(hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest())
PY
)
  (cd "$dir" && "${JAR_CMD[@]}" add README.md) || { echo "single-file add failed" > "$dir/error"; return 1; }
  [[ -f "$dir/.git/objects/${expected:0:2}/${expected:2}" ]] || { echo "blob was not stored" > "$dir/error"; return 1; }
  grep -qx "README.md $expected" <(read_index "$dir/.git/index") || { echo "README index entry is wrong" > "$dir/error"; return 1; }

  printf 'second version\n' > "$dir/README.md"
  updated=$(python3 - "$dir/README.md" <<'PY'
import hashlib
from pathlib import Path
import sys
data = Path(sys.argv[1]).read_bytes()
print(hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest())
PY
)
  (cd "$dir" && "${JAR_CMD[@]}" add ./README.md) || { echo "updated add failed" > "$dir/error"; return 1; }
  grep -qx "README.md $updated" <(read_index "$dir/.git/index") || { echo "README was not updated" > "$dir/error"; return 1; }

  (cd "$dir" && "${JAR_CMD[@]}" add a.txt b.txt c.txt >"$dir/missing.stdout" 2>"$dir/missing.stderr") && {
    echo "missing file unexpectedly succeeded" > "$dir/error"; return 1;
  }
  index_before=$(sha256sum "$dir/.git/index" | cut -d' ' -f1)
  (cd "$dir" && "${JAR_CMD[@]}" add a.txt missing.txt >"$dir/failure.stdout" 2>"$dir/failure.stderr") && {
    echo "failure-safety add unexpectedly succeeded" > "$dir/error"; return 1;
  }
  index_after=$(sha256sum "$dir/.git/index" | cut -d' ' -f1)
  [[ "$index_before" == "$index_after" ]] || { echo "failed add changed index" > "$dir/error"; return 1; }

  (cd "$dir" && "${JAR_CMD[@]}" add .) || { echo "directory add failed" > "$dir/error"; return 1; }
  local entries
  entries=$(read_index "$dir/.git/index")
  grep -qx 'a.txt [0-9a-f]\{40\}' <<< "$entries" || { echo "a.txt missing" > "$dir/error"; return 1; }
  grep -qx 'nested/file.txt [0-9a-f]\{40\}' <<< "$entries" || { echo "nested file missing" > "$dir/error"; return 1; }
  grep -qx 'empty.txt e69de29bb2d1d6434b8b29ae775ad8c2e48c5391' <<< "$entries" || { echo "empty file missing" > "$dir/error"; return 1; }
  grep -qx 'binary.dat [0-9a-f]\{40\}' <<< "$entries" || { echo "binary file missing" > "$dir/error"; return 1; }
  ! grep -q '^\.git/' <<< "$entries" || { echo ".git was staged" > "$dir/error"; return 1; }
  [[ $(grep -c '^README.md ' <<< "$entries") -eq 1 ]] || { echo "duplicate README entry" > "$dir/error"; return 1; }
  [[ $(grep -c '^README.md ' <<< "$(read_index "$dir/.git/index")") -eq 1 ]] || { echo "persistence check failed" > "$dir/error"; return 1; }
  return 0
}

case_write_tree_and_ls_tree() {
  local dir=$1
  mkdir -p "$dir/sub"
  printf 'alpha\n' > "$dir/a.txt"
  printf 'beta\n' > "$dir/sub/b.txt"
  git -C "$dir" init -q -b main || { echo "git init failed" > "$dir/error"; return 1; }
  git -C "$dir" add a.txt sub/b.txt || { echo "git add failed" > "$dir/error"; return 1; }
  local tree expected names
  tree=$(cd "$dir" && "${JAR_CMD[@]}" write-tree 2>/dev/null) || { echo "write-tree exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$tree" =~ ^[0-9a-f]{40}$ ]] || { echo "invalid tree hash: $tree" > "$dir/error"; return 1; }
  expected=$(cd "$dir" && git write-tree 2>/dev/null) || { echo "real git write-tree failed" > "$dir/error"; return 1; }
  [[ "$tree" == "$expected" ]] || { echo "tree differs from git: expected $expected, got $tree" > "$dir/error"; return 1; }
  names=$(cd "$dir" && "${JAR_CMD[@]}" ls-tree --name-only "$tree" 2>/dev/null) || { echo "ls-tree exited unsuccessfully" > "$dir/error"; return 1; }
  grep -qx 'a.txt' <<< "$names" || { echo "ls-tree omitted a.txt: $names" > "$dir/error"; return 1; }
  grep -qx 'sub' <<< "$names" || { echo "ls-tree omitted sub: $names" > "$dir/error"; return 1; }
  return 0
}

case_commit_tree() {
  local dir=$1
  local tree commit
  tree=$(cd "$dir" && "${JAR_CMD[@]}" write-tree 2>"$dir/stderr") || { echo "write-tree prerequisite failed" > "$dir/error"; return 1; }
  commit=$(cd "$dir" && "${JAR_CMD[@]}" commit-tree "$tree" -p 0000000000000000000000000000000000000000 -m "initial commit" 2>>"$dir/stderr") || { echo "commit-tree exited unsuccessfully" > "$dir/error"; return 1; }
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo "invalid commit hash: $commit" > "$dir/error"; return 1; }
  assert_file "$dir/.git/objects/${commit:0:2}/${commit:2}" || return 1
  return 0
}

case_failures() {
  local dir=$1
  local output
  output=$(cd "$dir" && "${JAR_CMD[@]}" cat-file -p 0000000000000000000000000000000000000000 2>"$dir/stderr") || { echo "missing-object command crashed" > "$dir/error"; return 1; }
  [[ "$output" == *"Object not found"* ]] || { echo "missing-object message was: $output" > "$dir/error"; return 1; }
  return 0
}

case_clone() {
  local dir=$1
  local source="$dir/source" destination="$dir/clone" server_log="$dir/server.log" port_file="$dir/port"
  mkdir -p "$source"
  git -C "$source" init -q -b main
  git -C "$source" config user.name 'Local Test'
  git -C "$source" config user.email 'local@example.test'
  printf 'tracked content\n' > "$source/README.txt"
  mkdir "$source/nested"
  printf 'nested content\n' > "$source/nested/file.txt"
  git -C "$source" add .
  git -C "$source" commit -q -m 'clone fixture'
  python3 "$ROOT_DIR/tests/git_http_server.py" "$source" "$port_file" >"$server_log" 2>&1 &
  local server_pid=$!
  for _ in $(seq 1 50); do [[ -s "$port_file" ]] && break; sleep 0.02; done
  local port
  port=$(cat "$port_file" 2>/dev/null) || { kill "$server_pid" 2>/dev/null || true; echo "HTTP server did not start" > "$dir/error"; return 1; }
  (cd "$dir" && "${JAR_CMD[@]}" clone "http://127.0.0.1:$port/source" clone >"$dir/clone.stdout" 2>"$dir/clone.stderr") || { kill "$server_pid" 2>/dev/null || true; echo "clone exited unsuccessfully" > "$dir/error"; return 1; }
  kill "$server_pid" 2>/dev/null || true
  [[ $(cat "$destination/README.txt") == "tracked content" ]] || { echo "cloned README content mismatch" > "$dir/error"; return 1; }
  [[ $(cat "$destination/nested/file.txt") == "nested content" ]] || { echo "cloned nested content mismatch" > "$dir/error"; return 1; }
  assert_file "$destination/.git/HEAD" || return 1
  assert_file "$destination/.git/refs/heads/main" || return 1
  assert_dir "$destination/.git/objects" || return 1
  return 0
}

printf 'Building implementation...\n'
build_log="$CASE_DIR/build.log"
if mvn -q -B package -Ddir="$BUILD_DIR" -f "$ROOT_DIR/pom.xml" >"$build_log" 2>&1 && [[ -f "$JAR" ]]; then
  JAR_CMD=(java --enable-preview -jar "$JAR")
  run_case 'init creates Git structure' case_init
  run_case 'hash-object stores and cat-file retrieves blob' case_hash_and_cat_file
  run_case 'hash-object handles empty files' case_hash_edge_cases
  run_case 'add stages and persists normalized entries' case_add
  run_case 'write-tree matches Git and ls-tree lists entries' case_write_tree_and_ls_tree
  run_case 'commit-tree writes a commit object' case_commit_tree
  run_case 'cat-file reports missing objects' case_failures
  run_case 'clone fetches and checks out a real Git repository' case_clone
else
  detail=$(tr '\n' ' ' < "$build_log" | sed 's/[[:space:]]\+/ /g' | cut -c1-300)
  record FAIL 'implementation build' "$detail"
  for name in 'init creates Git structure' 'hash-object stores and cat-file retrieves blob' 'hash-object handles empty files' 'write-tree matches Git and ls-tree lists entries' 'commit-tree writes a commit object' 'cat-file reports missing objects' 'clone fetches and checks out a real Git repository'; do
    record SKIP "$name" 'implementation did not build'
  done
fi

printf '\nSummary: %d passed, %d failed, %d skipped\n' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT"
if (( FAIL_COUNT > 0 )); then
  printf 'Build details: %s\n' "$build_log"
  exit 1
fi