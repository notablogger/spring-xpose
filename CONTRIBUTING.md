# Contributing to spring-xpose

Thank you for your interest in contributing! This guide covers everything you need to get started.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [How to Contribute](#how-to-contribute)
3. [Branch Strategy](#branch-strategy)
4. [Development Setup](#development-setup)
5. [Project Structure](#project-structure)
6. [Making Changes](#making-changes)
7. [Testing Guidelines](#testing-guidelines)
8. [Commit Convention](#commit-convention)
9. [Pull Request Process](#pull-request-process)
10. [Release Process](#release-process)

---

## Code of Conduct

Be respectful. Critique code, not people. We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

---

## How to Contribute

| Want to… | Do this |
|---|---|
| Report a bug | [Open a Bug Report](https://github.com/notablogger/spring-xpose/issues/new?template=bug_report.yml) |
| Request a feature | [Open a Feature Request](https://github.com/notablogger/spring-xpose/issues/new?template=feature_request.yml) |
| Ask a question | [Start a Discussion](https://github.com/notablogger/spring-xpose/discussions) |
| Submit a fix / feature | Fork → branch → PR (see below) |

For anything non-trivial (new annotation attributes, changes to generated code shape) **open an issue first** so we can agree on the design before you write code.

---

## Branch Strategy

| Branch | Purpose | Protected? | Who pushes? |
|---|---|---|---|
| `main` | Latest stable release; HEAD is always green | ✅ Yes | Maintainers via PR merge only |
| `feat/<short-description>` | New features, e.g. `feat/patch-support` | No | Contributors |
| `fix/<short-description>` | Bug fixes, e.g. `fix/version-field-null` | No | Contributors |
| `docs/<short-description>` | Documentation-only changes | No | Contributors |
| `chore/<short-description>` | Build, CI, dependency changes | No | Contributors |
| `release/x.y.z` | Release preparation branch | ✅ Yes | Maintainers only |

**Rules enforced on `main`:**
- Direct push is blocked — all changes arrive via Pull Request
- At least 1 approving review required
- The `PR Build` status check must pass before merge
- Branch must be up-to-date with `main` before merge
- No force-push, no deletion

**Branch naming:** use `type/kebab-case-description`. PRs with branches that don't match `feat/*`, `fix/*`, `docs/*`, `chore/*`, `test/*`, or `release/*` will be flagged by the PR build.

---

## Development Setup

### Prerequisites

- Java 21+
- Gradle (wrapper included — use `./gradlew`)
- Git

### Clone and build

```bash
git clone https://github.com/notablogger/spring-xpose.git
cd spring-xpose
./gradlew build -x javadoc
```

### Run all tests

```bash
# Processor unit tests (45 tests, fast — ~10s)
./gradlew :processor:test

# Sample integration tests (26 tests — ~20s)
cd ../spring-xpose-sample-rest
./gradlew test
```

### Publish to local Maven (required before testing sample)

```bash
cd spring-xpose
./gradlew publishToMavenLocal
```

### IDE setup

IntelliJ IDEA works best. Open the root `spring-xpose` directory. The Gradle project auto-imports all three modules (`annotations`, `processor`, `starter`).

> **Tip:** Generated sources land in `build/generated/sources/annotationProcessor/`. Add this directory as a source root in your IDE if it isn't already.

---

## Project Structure

```
spring-xpose/
├── annotations/          # @ExposeEntity annotation + enums (no deps)
│   └── src/main/java/
│       └── io/github/notablogger/springxpose/annotation/
├── processor/            # APT — reads @ExposeEntity, writes .java files
│   └── src/main/java/
│       └── io/github/notablogger/springxpose/processor/
│           ├── ExposeEntityProcessor.java   ← entry point (@AutoService)
│           ├── model/                       ← EntityModel, FieldModel, etc.
│           ├── generator/                   ← one class per generated artifact
│           └── util/                        ← EntityNameUtils
├── starter/              # Spring Boot autoconfiguration + runtime support
│   └── src/main/java/
│       └── io/github/notablogger/springxpose/
│           ├── config/                      ← AutoConfiguration, Properties
│           ├── exception/                   ← SpringXposeExceptionHandler
│           └── serializer/                  ← RelationAwareSerializer
└── docs/
    └── tech/             # Architecture docs, class diagrams, generator guide
```

See [`docs/tech/architecture.md`](docs/tech/architecture.md) for the full annotated class diagram and data-flow walkthrough.

---

## Making Changes

### Adding a new `@ExposeEntity` attribute

1. Add the attribute to `annotations/src/.../ExposeEntity.java` with Javadoc
2. Parse it in `processor/.../model/EntityModel.java` (`parse()` method)
3. Use it in the relevant generator(s) under `processor/.../generator/`
4. Add processor unit tests in `ExposeEntityProcessorTest` or a dedicated test class
5. Update the sample entity in `spring-xpose-sample-rest` if it illustrates the feature
6. Update `README.md` attribute table and `docs/tech/architecture.md`

### Adding a new generated artifact

1. Create `processor/.../generator/MyNewGenerator.java`
2. Invoke it from `ExposeEntityProcessor.process()`
3. Add to the `EntityModel` if new data is needed
4. Add at least one processor test asserting the file is generated and has correct content
5. Document it in `docs/tech/generated-artifacts.md`

### Changing the starter / exception handler

The starter is a regular Spring Boot autoconfiguration module. Changes here affect runtime behaviour of all users — test thoroughly in the sample project.

---

## Testing Guidelines

### Processor unit tests (`processor/src/test/`)

- Use `com.google.testing.compile` to compile source strings in-memory
- Every generator change needs a test asserting the generated source contains / does not contain specific strings
- Test both the happy path and edge cases (no relations, all operations, only read operations, etc.)
- Run: `./gradlew :processor:test`

### Sample integration tests (`spring-xpose-sample-rest/src/test/`)

- These are full Spring Boot context tests (`@SpringBootTest`)
- They catch runtime wiring issues the unit tests can't see
- Required when changing the starter, exception handler, or controller generation
- Run: `cd ../spring-xpose-sample-rest && ./gradlew test`

### What "done" looks like

- All 45 processor unit tests pass
- All 26 sample integration tests pass
- Zero MapStruct warnings during `compileJava` in the sample project
- No new Gradle warnings introduced

---

## Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): short imperative description

Optional body: explain the why, not the what.

Closes #123
```

**Types:** `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`

**Scopes:** `annotations`, `processor`, `starter`, `sample`, `ci`, `docs`

**Examples:**
```
feat(processor): add pageable=true support for findAll endpoint
fix(processor): explicitly ignore @Version field in toEntity mapper method
docs(tech): add Mermaid class diagram for generator pipeline
chore(ci): add PR build workflow with branch name validation
```

Commits that don't follow this format will be flagged in the PR build.

---

## Pull Request Process

1. Fork the repo and create your branch from `main` using the naming convention above
2. Make your changes following the guidelines in this document
3. Ensure all tests pass locally before pushing
4. Open a PR — fill in the PR template completely
5. The PR Build workflow runs automatically — fix any failures
6. A maintainer will review. Address feedback with new commits (don't force-push during review)
7. Once approved and all checks pass, a maintainer will squash-merge

**PR title** must also follow the commit convention: `type(scope): description`.

---

## Release Process

Releases are **maintainer-only**. The flow is:

1. Create a `release/x.y.z` branch from `main`
2. Bump the version in `build.gradle` if needed
3. Merge to `main` via PR
4. Push a `vx.y.z` tag — the `Publish to Maven Central` workflow fires automatically
5. GitHub Release is created with the JARs attached

Contributors do not need to worry about this.

