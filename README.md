# Git From Scratch in Java

A from-scratch Java implementation of selected Git internals: content-addressed objects, repository initialization, tree and commit creation, and HTTP cloning with packfile reconstruction.

This is an educational Git implementation, not a complete replacement for Git. It focuses on making the object model and repository mechanics understandable while leaving a clear path for future commands.

## Overview

The project explores how Git represents and transfers data without delegating its core object operations to the Git CLI. It currently implements:

- Loose blob, tree, and commit objects
- SHA-1 object IDs and zlib-compressed object storage
- A minimal `.git` repository layout
- Recursive working-directory tree creation
- Tree parsing and listing
- Git HTTP smart-protocol ref discovery
- Packfile parsing, delta reconstruction, and clone checkout

The implementation is organized into CLI, command, object, repository, transport, and utility layers so future Git functionality does not need to be added to `Main.java`.

## Current Capabilities

| Command / feature | Status | Current behavior |
| --- | --- | --- |
| `init` | Implemented | Creates `.git/objects`, `.git/refs/heads`, and `HEAD` pointing to `refs/heads/main`. |
| `hash-object` | Implemented | Reads a file, creates and stores a blob, then prints its SHA-1. |
| `cat-file` | Implemented | Reads a loose object by hash, decompresses it, and prints its payload. |
| `write-tree` | Implemented | Recursively scans the working directory, skips `.git`, and stores a tree. It does not use `.git/index`. |
| `ls-tree` | Implemented | Parses a tree and prints entry names. Current syntax expects the hash at argument position 2. |
| `commit-tree` | Implemented | Creates a commit object from supplied tree, parent, and message arguments. It does not move a branch ref. |
| `clone` | Implemented | Downloads refs and a packfile over HTTP, stores objects, writes refs, and checks out the target tree. |
| `add` | Planned | Requires a real Git index. |
| `status` | Planned | Requires comparisons between the working directory, index, and `HEAD`. |
| `commit` | Planned | Will combine the index, tree creation, commit creation, and ref updates. |
| `log` | Planned | Will traverse commit parents. |
| `branch` | Planned | Will create, list, and move branch refs. |
| `checkout` | Planned | Will update the working tree and `HEAD`. |
| `merge` | Planned | Will combine commit histories and trees. |
| `fetch` | Planned | Will download objects and remote refs without checkout. |
| `pull` | Planned | Will combine fetch with integration. |
| `push` | Planned | Will upload objects and request remote ref updates. |

### Command syntax

The current implementation preserves the argument positions used by the local harness:

```sh
hash-object <file>
cat-file -p <object-sha>
write-tree
ls-tree --name-only <tree-sha>
commit-tree <tree-sha> -p <parent-sha> -m "message"
clone <url> [directory]
```

The `ls-tree` option is not parsed; it places the tree hash in `args[2]`. `hash-object` reads `args[1]` for the simple form and uses `args[2]` when a third argument is supplied.

## How Git Works in This Project

### Current object flow

```text
Working Directory
       │
       ▼
     Blob
       │
       ▼
     Tree
       │
       ▼
    Commit
       │
       ▼
     Refs
       │
       ▼
      HEAD
```

- **Working Directory**: files on disk.
- **Blob**: content of an individual file.
- **Tree**: names, modes, and object IDs for files and directories.
- **Commit**: metadata pointing to a root tree and parent commit.
- **Refs**: names such as `refs/heads/main` that point to commits.
- **HEAD**: the current symbolic reference. `init` writes `ref: refs/heads/main`.

The current `write-tree` scans the working directory directly. The current `commit-tree` creates an object but does not update a branch ref.

### Future index flow

```text
Working Directory
       │
     git add
       ▼
     Index
       │
  write-tree
       ▼
     Tree
       │
    commit
       ▼
    Commit
```

The index and `git add` are not implemented yet. `repository/GitIndex.java` is currently an architectural placeholder for the future `.git/index` implementation.

## Git Object Model

The project uses Git's three core object types:

```text
Blob   → file contents
Tree   → directory structure
Commit → snapshot and history metadata
```

Every object is encoded as:

```text
<type> <size>\0<content>
```

For example, a blob containing `hello\n` is conceptually:

```text
blob 6\0hello\n
```

The SHA-1 is calculated over the complete header-plus-content byte sequence. The hexadecimal object ID is then stored using Git's loose-object layout:

```text
.git/objects/<first-two-hash-characters>/<remaining-38-characters>
```

The stored file contains the complete object compressed with zlib.

## Implemented Features

### `init`

`InitCommand` asks `GitRepository` to create:

```text
.git/
├── objects/
├── refs/
│   └── heads/
└── HEAD
```

`HEAD` contains:

```text
ref: refs/heads/main
```

This is a deliberately minimal repository setup. It does not create an initial commit or branch file.

### `hash-object`

`HashObjectCommand` delegates file reading and blob creation to `Blob`. The file bytes are combined with the `blob <size>\0` header, hashed with SHA-1, compressed with zlib, and written through `ObjectStore`.

Empty files are supported. Their object content is still hashed with the `blob 0\0` header, which produces the standard empty-blob ID.

### `cat-file`

`CatFileCommand` builds the loose-object path from the supplied hash. `ObjectStore` decompresses the object and removes the header, leaving only the payload for output. A missing object prints `Object not found: <hash>`.

The current command prints payloads as UTF-8 text, so it is intended for the text-oriented behavior covered by the local tests rather than arbitrary binary display.

### `write-tree`

`WriteTreeCommand` calls `Tree.write` on the current directory. `Tree.write` sorts entries by filename, skips `.git`, creates blobs for files, recursively creates trees for directories, and serializes entries as:

```text
<mode> <name>\0<raw 20-byte object ID>
```

Regular files use mode `100644`; directories use mode `40000`. The current implementation scans the working tree directly instead of reading the Git index.

### `ls-tree`

`LsTreeCommand` reads a complete tree object through `ObjectStore`, then `Tree.entries` parses its binary entries. The command prints each entry name. It does not recursively list child trees or display modes.

### `commit-tree`

`CommitTreeCommand` passes the supplied values to `Commit.write`. The current commit payload contains:

```text
tree <tree-sha>
parent <parent-sha>
author James Gosling <james@nighthacks.com>
committer James Gosling <james@nighthacks.com>

<message>
```

The fixed identity and absence of timestamps are intentional limitations of this learning implementation. The object is stored and its ID is printed, but the current command does not update `refs/heads/main`.

### `clone`

`CloneCommand` coordinates the current clone workflow:

```text
Remote Repository
       ↓
HTTP Smart Protocol
       ↓
Refs / Packfile
       ↓
Pack Parsing
       ↓
Git Objects
       ↓
Working Tree
```

It discovers the remote `HEAD` and default branch, requests a packfile, reconstructs pack objects, stores them as loose objects, writes local `HEAD` and branch metadata, reads the target commit's tree, and recursively checks out files.

## Clone and Git HTTP Implementation

The clone path is intentionally focused on the protocol shape exercised by the local test harness. It is not a complete implementation of every Git transport negotiation or server feature.

The relevant classes are:

- `CloneCommand`: destination setup, orchestration, refs, and recursive checkout.
- `GitHttpClient`: HTTP requests, ref discovery, pkt-line reading, sideband pack extraction, and upload-pack requests.
- `PackFile`: pack header parsing, object type decoding, zlib stream handling, OFS/REF delta reconstruction, and object ID calculation.
- `PackObject`: intermediate fields for one unpacked pack object.

The discovery request is made to `info/refs?service=git-upload-pack`. The fetch request is posted to `git-upload-pack` with a `want` line for the target commit. Pkt-lines begin with a four-hexadecimal-digit length, which `GitHttpClient.readPktLine` uses to extract each response line.

Pack objects may be normal objects, OFS deltas, or REF deltas. `PackFile` reconstructs delta content from an earlier object by applying copy and insert instructions. The resulting payload is written to the destination object store, and `CloneCommand` uses tree entries to create the working files.

## Project Architecture

```text
src/main/java/
├── Main.java
├── cli/
│   └── CommandDispatcher.java
├── commands/
│   ├── CatFileCommand.java
│   ├── CloneCommand.java
│   ├── CommitTreeCommand.java
│   ├── GitCommand.java
│   ├── HashObjectCommand.java
│   ├── InitCommand.java
│   ├── LsTreeCommand.java
│   └── WriteTreeCommand.java
├── objects/
│   ├── Blob.java
│   ├── Commit.java
│   ├── GitObject.java
│   └── Tree.java
├── repository/
│   ├── GitIndex.java
│   ├── GitRepository.java
│   └── ObjectStore.java
├── transport/
│   ├── GitHttpClient.java
│   ├── PackFile.java
│   └── PackObject.java
└── utils/
    ├── CompressionUtils.java
    ├── FileUtils.java
    └── HashUtils.java
```

### Package responsibilities

- `cli/`: maps command names to command objects.
- `commands/`: user-facing implementations of the seven currently supported commands.
- `objects/`: Git object encoding, blob creation, tree serialization/parsing, and commit serialization.
- `repository/`: repository paths, loose object persistence, and the future index location.
- `transport/`: HTTP smart-protocol requests and packfile processing.
- `utils/`: SHA-1, hexadecimal formatting, zlib, and small filesystem helpers.

### Class reference

| Class | Responsibility |
| --- | --- |
| `Main` | Starts the application and delegates to `CommandDispatcher`. |
| `CommandDispatcher` | Matches `args[0]` to a `GitCommand`. |
| `GitCommand` | Common command interface with `execute(String[] args)`. |
| `InitCommand` | Initializes the minimal repository layout. |
| `HashObjectCommand` | Creates and stores a blob from a file. |
| `CatFileCommand` | Retrieves and prints an object payload. |
| `WriteTreeCommand` | Creates a tree from the current working directory. |
| `LsTreeCommand` | Parses and prints tree entry names. |
| `CommitTreeCommand` | Creates a low-level commit object. |
| `CloneCommand` | Coordinates remote download and checkout. |
| `GitObject` | Encodes object headers and locates payload bytes. |
| `Blob` | Reads a file and stores it as a blob. |
| `Tree` | Writes directories and parses binary tree entries. |
| `Commit` | Serializes the current commit format. |
| `GitRepository` | Owns repository root, `.git`, `HEAD`, branch, and object-store paths. |
| `ObjectStore` | Hashes, compresses, stores, decompresses, and reads loose objects. |
| `GitIndex` | Empty future extension point for `.git/index`. |
| `GitHttpClient` | Performs clone's HTTP ref and pack requests. |
| `PackFile` | Parses pack objects and applies deltas. |
| `PackObject` | Holds one unpacked pack object. |
| `HashUtils` | Provides SHA-1 and hexadecimal conversion. |
| `CompressionUtils` | Provides zlib compression and decompression. |
| `FileUtils` | Provides a small `writeString` filesystem helper; it is not used by the current command flow. |

## Running the Project

### Requirements

- Java 21
- Maven
- Git CLI
- Python 3 for the local clone test server

The Java version and compiler target are set to 21 in `pom.xml`. The tests use the Git CLI to create reference repositories and Python to run `git http-backend` locally.

### Build

```sh
mvn clean package
```

The assembly plugin creates:

```text
target/codecrafters-git.jar
```

Maven currently emits warnings because some plugin versions and assembly options are not explicitly pinned, but the build succeeds in the verified environment.

### Run the generated JAR

From the project root, run commands in a separate temporary directory so the project's own `.git` directory is not modified:

```sh
mvn clean package
mkdir -p /tmp/git-from-scratch-demo
cd /tmp/git-from-scratch-demo
java --enable-preview -jar /path/to/codecrafters-git.jar init
```

Alternatively, use the repository launcher from a temporary working directory:

```sh
cd /tmp/git-from-scratch-demo
/path/to/codecrafters-git-java/your_program.sh init
```

`your_program.sh` packages the project into `/tmp/codecrafters-build-git-java` and runs the assembled JAR.

## Testing

Run the complete local suite with:

```sh
./run-tests.sh
```

The harness:

- Builds the implementation into a temporary directory.
- Creates independent temporary directories for each test.
- Checks `init`, blob hashing/retrieval, empty blobs, trees, commits, and missing-object behavior.
- Creates a real temporary Git repository for the tree comparison.
- Uses the real Git CLI as a reference where appropriate.
- Starts `tests/git_http_server.py`, which serves a temporary repository through `git http-backend` for clone testing.
- Reports individual `PASS`, `FAIL`, and `SKIP` results.

The verified current result is:

```text
7 passed
0 failed
0 skipped
```

This is a seven-scenario integration suite, not proof that the project is a complete Git implementation.

## Example Usage

Use a temporary directory:

```sh
mkdir -p /tmp/git-from-scratch-demo
cd /tmp/git-from-scratch-demo

# Initialize the minimal repository.
/path/to/codecrafters-git-java/your_program.sh init

# Create a file and store it as a blob.
printf 'hello from Java Git\n' > README.md
blob_sha=$(/path/to/codecrafters-git-java/your_program.sh hash-object README.md)
printf 'blob: %s\n' "$blob_sha"

# Read the stored blob back.
/path/to/codecrafters-git-java/your_program.sh cat-file -p "$blob_sha"

# Create and print a tree for the current working directory.
tree_sha=$(/path/to/codecrafters-git-java/your_program.sh write-tree)
printf 'tree: %s\n' "$tree_sha"

# List root tree entries. The implementation expects the hash at argument 2.
/path/to/codecrafters-git-java/your_program.sh ls-tree --name-only "$tree_sha"

# Create a low-level commit object. This prints a commit SHA but does not move
# refs/heads/main.
commit_sha=$(/path/to/codecrafters-git-java/your_program.sh commit-tree \
  "$tree_sha" \
  -p 0000000000000000000000000000000000000000 \
  -m "initial snapshot")
printf 'commit: %s\n' "$commit_sha"
```

The exact author identity and commit metadata are defined in `objects/Commit.java`.

## Design Decisions

- **CLI** isolates argument dispatch from application startup.
- **Commands** keep user-facing workflows separate and give future commands obvious homes.
- **Objects** own Git's byte-level blob, tree, and commit formats.
- **Repository** owns `.git` paths and loose object persistence.
- **Transport** isolates HTTP and packfile mechanics from command orchestration.
- **Utils** centralize reusable hashing, compression, and filesystem operations.

This separation makes it possible to add `AddCommand`, `StatusCommand`, and later history or transport commands without expanding `Main.java` into another monolithic implementation.

## Roadmap

```text
[x] init
[x] hash-object
[x] cat-file
[x] write-tree
[x] ls-tree
[x] commit-tree
[x] clone

[ ] git add
[ ] git status
[ ] git commit
[ ] git log
[ ] git branch
[ ] git checkout
[ ] git merge
[ ] git fetch
[ ] git pull
[ ] git push
```

The logical next step is the index, followed by `git add`, because normal Git commit workflows depend on the staging area.

## What This Project Explores

- Content-addressable storage
- SHA-1 object IDs
- Blob, tree, and commit objects
- The `.git` repository layout
- zlib compression and decompression
- Binary tree serialization and parsing
- Commit serialization
- References and symbolic `HEAD`
- Git's HTTP smart protocol
- Packfile parsing and delta reconstruction
- Recursive checkout
- Layered architecture for extending a systems-oriented codebase

## Documentation

- [`ARCHITECTURE.md`](ARCHITECTURE.md) is the concise map of project layers, Git data flow, and future command locations.
- [`PROJECT_DETAILED.md`](PROJECT_DETAILED.md) is the source-specific deep dive into classes, methods, object bytes, clone internals, tests, and future implementation work.

## Technical Highlights

- Git object storage implemented with SHA-1 addressing and zlib-compressed loose objects.
- Recursive tree construction using raw 20-byte child object IDs.
- Binary tree parsing for `ls-tree` and clone checkout.
- Commit object creation using Git's header-and-payload format.
- HTTP smart-protocol ref discovery and packfile requests.
- Pack object parsing with OFS and REF delta reconstruction.
- Recursive checkout of the cloned commit tree.
- A dependency-light temporary-repository test harness that compares selected behavior with real Git.

## Limitations

This project should be described as a from-scratch implementation of selected Git internals and repository operations. It is not a complete Git replacement.

In particular, the current source does not implement `git add`, `.git/index`, user-facing `git commit`, `status`, `log`, branch management, checkout as a standalone command, merge, fetch, pull, or push. The clone path is intentionally limited to the HTTP/pack behavior currently handled by `GitHttpClient` and `PackFile`.
