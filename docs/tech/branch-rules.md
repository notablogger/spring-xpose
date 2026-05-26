# Branch Rules — spring-xpose

This document defines the branching strategy, naming conventions, and the GitHub branch protection rules that enforce them.

---

## Branch model

spring-xpose follows a lightweight **trunk-based** model. `main` is the single long-lived branch. All work arrives via short-lived feature / fix branches merged as PRs.

```
main  ←──── feat/pageable-find-all        (merged, deleted)
      ←──── fix/version-field-mapping      (merged, deleted)
      ←──── docs/tech-architecture          (merged, deleted)
      ←──── release/0.4.0                  (merged, tagged, deleted)
```

---

## Branch inventory

| Branch pattern | Purpose | Protected | Who creates |
|---|---|---|---|
| `main` | Stable trunk — HEAD is always green and releasable | ✅ Yes | — (merge-only) |
| `feat/<description>` | New feature or annotation attribute | No | Contributors |
| `fix/<description>` | Bug fix | No | Contributors |
| `docs/<description>` | Documentation-only change | No | Contributors |
| `test/<description>` | Test additions or improvements with no behaviour change | No | Contributors |
| `refactor/<description>` | Code restructure with no behaviour change | No | Contributors |
| `chore/<description>` | Build, dependency, CI, tooling change | No | Contributors |
| `ci/<description>` | Workflow / pipeline change | No | Contributors |
| `release/x.y.z` | Release preparation — version bump and changelog | ✅ Yes | Maintainers only |

---

## Naming rules

Branch names must match:

```
^(feat|fix|docs|test|chore|refactor|ci|release)/[a-z0-9][a-z0-9\-]*[a-z0-9]$
```

- **kebab-case only** — lowercase letters, digits, hyphens
- **No slashes beyond the type prefix** — `feat/add-patch-support`, not `feat/JIRA-123/add-patch-support`
- **Descriptive but concise** — 3–6 words is a good target

### Valid examples

```
feat/pageable-find-all
fix/null-version-field-mapper
docs/tech-architecture-diagram
chore/bump-javapoet-2-1
ci/add-pr-title-lint
release/0.4.0
```

### Invalid examples

```
feature/pageable       ← "feature" is not an allowed type prefix
feat/ADD_PAGEABLE      ← uppercase not allowed
feat/pageable-        ← trailing hyphen
my-branch              ← no type prefix
```

The PR Build workflow (`pr.yml`) validates the branch name on every PR and fails immediately if the name does not match.

---

## Commit message rules

Commits must follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
type(scope): short imperative description

Optional body — explain the why, not the what.

Closes #123
```

| Element | Allowed values |
|---|---|
| `type` | `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `perf`, `revert` |
| `scope` | `annotations`, `processor`, `starter`, `sample`, `ci`, `docs` |
| Description | ≤ 72 characters, imperative mood, no trailing period |

The **PR title** must also follow this format — it becomes the squash-merge commit message.

---

## Protection rules on `main`

The following rules are enforced via GitHub's **Branch protection rules** (Settings → Branches → `main`):

| Rule | Setting |
|---|---|
| Require a pull request before merging | ✅ Enabled |
| Required approving reviews | 1 (maintainers) |
| Dismiss stale reviews when new commits are pushed | ✅ Enabled |
| Require review from Code Owners | ✅ Enabled (when `CODEOWNERS` exists) |
| Require status checks to pass before merging | ✅ Enabled |
| Required status checks | `PR gate (all checks passed)` |
| Require branches to be up to date before merging | ✅ Enabled |
| Require conversation resolution before merging | ✅ Enabled |
| Restrict who can push to matching branches | Maintainers only |
| Allow force pushes | ❌ Disabled |
| Allow deletions | ❌ Disabled |

> **Configuring required checks:** Go to **Settings → Branches → main → Edit** and add `PR gate (all checks passed)` as a required status check. This is the single gate job in `pr.yml` that only passes when all upstream jobs (`branch-lint`, `title-lint`, `test`, `build-check`) succeed.

---

## Protection rules on `release/*`

| Rule | Setting |
|---|---|
| Require a pull request before merging | ✅ Enabled |
| Required approving reviews | 1 (maintainers) |
| Restrict who can push | Maintainers only |
| Allow force pushes | ❌ Disabled |

---

## Automated enforcement (PR Build workflow)

The `pr.yml` workflow runs on every PR targeting `main` and enforces:

1. **Branch name** — regex match against the allowed pattern (job: `branch-lint`)
2. **PR title** — Conventional Commits format check (job: `title-lint`)
3. **Processor unit tests** — `./gradlew :processor:test` (job: `test`)
4. **JAR assembly** — `./gradlew assemble -x javadoc` (job: `build-check`)
5. **Gate job** — `PR gate (all checks passed)` — fails if any upstream job failed

All four jobs must pass for the gate to be green. GitHub's branch protection requires the gate before merge.

Concurrency is configured with `cancel-in-progress: true` so pushing a new commit to an open PR cancels the previous in-progress run.

---

## Merge strategy

All PRs are **squash-merged**. This keeps `main` history flat and linear — one commit per PR, with a Conventional Commit message (taken from the PR title).

Contributors should not rebase or force-push after a review has started. Address reviewer feedback with new commits; the maintainer squashes everything on merge.

---

## Working on a contribution — quick reference

```bash
# 1. Fork and clone
git clone https://github.com/<your-username>/spring-xpose.git
cd spring-xpose

# 2. Create your branch from main
git switch -c feat/my-feature main

# 3. Make changes, build, test
./gradlew :processor:test

# 4. Commit with a Conventional Commit message
git commit -m "feat(processor): add my-feature support"

# 5. Push and open a PR — title must match the commit format
git push origin feat/my-feature
```

