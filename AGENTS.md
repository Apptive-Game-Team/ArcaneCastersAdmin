# Repository Guidelines

## Project Structure & Module Organization
This repository is a Spring Boot 3 admin server built with Gradle and Java 21. Application code lives under `src/main/java/com/wordonline/admin`, grouped by role: `controller`, `service`, `repository`, `entity`, `dto`, `config`, `security`, and `client`. Thymeleaf templates are in `src/main/resources/templates`, and static assets are in `src/main/resources/static`. Keep new packages aligned with this layout; for example, add new admin endpoints in `controller` and persistence logic in the matching `repository` package.

## Build, Test, and Development Commands
- `./gradlew bootRun`: run the server locally on `PORT` or `8080`.
- `./gradlew clean build`: compile, run tests, and build the executable JAR.
- `./gradlew test`: run the JUnit 5 test suite only.
- `docker build -t word-online-admin .`: build the container image from the multi-stage `Dockerfile`.
- `./remote-deploy.sh`: build and deploy to the configured remote host. Requires `DEPLOY_USER` and `DEPLOY_SERVER`.

Local configuration is loaded from `src/main/resources/application.yml` and optional `.env[.properties]`. Set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PW`, and `JWT_FILE_PATH` before running locally. Token verification reads the ACCOUNT server's address from the `servers` table and fetches its JWKS from `/.well-known/jwks`; there is no local public-key configuration for this. Shared game database changes belong in `../database/migration`; do not add production SQL under this repository's runtime resources.

## Coding Style & Naming Conventions
Use 4-space indentation and standard Java formatting. Class names use `PascalCase`; methods, fields, and variables use `camelCase`; package names stay lowercase. Keep controller classes suffixed with `Controller`, service classes with `Service`, repositories with `Repository`, and request/response payloads with `Dto` or `RequestDto`. Prefer constructor injection; this codebase uses Lombok annotations such as `@RequiredArgsConstructor`.

## Testing Guidelines
Tests should use `spring-boot-starter-test` with JUnit 5. Mirror the production package structure under `src/test/java`; for example, test `controller/HealthController` in `src/test/java/com/wordonline/admin/controller/HealthControllerTest.java`. Add focused tests for controllers, services, and repositories when behavior changes. Run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines

Recent history uses short conventional-style prefixes such as `feat:`, `fix:`, and `refactor(...)`, often with linked issue numbers like `(#36)`. Keep commits scoped to one concern. PRs should describe behavior changes, link the GitHub issue, mention affected endpoints, and note any required database migration or client/game-server/account-server coordination.

Workflow for tracked work in this repo:
- Create a GitHub issue before implementation when the user asks for end-to-end delivery.
- Create and work on a dedicated branch per issue.
- Use branch names in the form `<issue-label>/<issue-num>` such as `feature/123` or `fix/39`.
- After implementation and verification, open a PR linked to the issue.

Every issue and pull request must set an assignee and a label. Do not leave either blank.

- Assignee: `--assignee @me`.
- Label: use the same value as the branch prefix. Check available labels with `gh label list`; do not invent a new label when none fit.
- Do not attach a project.
- When GitHub CLI authentication appears invalid inside a sandbox but the user says their session is valid, request escalated execution and retry `gh` with the user's session credentials before asking them to re-authenticate.

```bash
gh issue create --title "..." --body "..." --assignee @me --label documentation
gh pr create --base <base> --title "..." --body "..." --assignee @me --label documentation
```

Confirm the metadata after creation:

```bash
gh issue view <issue-number> --json assignees,labels
gh pr view <pr-number> --json assignees,labels
```

## Security & Configuration Tips
Do not commit `.env`, database credentials, JWT material, or deployment host details. Review `deploy.sh` and `remote-deploy.sh` carefully before changing release behavior because they manage live process restarts.

## Versioning

`version` in `build.gradle` is the admin server's single version source.
Do not bump it in a pull request. The monorepo `deploy` skill bumps it once per
promotion: it commits `chore(release): WordOnlineAdmin vX.Y.Z` to `main`, merges
`main` into `deploy`, then tags and releases `vX.Y.Z` on the merge commit. The level comes
from the Conventional Commit messages promoted in that release: MAJOR for a `!`
marker or a `BREAKING CHANGE` trailer, MINOR for `feat:`, PATCH otherwise, so
write accurate commit types.

Never add a second runtime version or use a `-SNAPSHOT` deployable version.
Spring Boot build info embeds this value.
