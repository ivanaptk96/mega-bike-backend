# Mega Bike Git Workflow

## 1. Before Committing

Check the current branch:

```bash
git branch --show-current
```

Check changed files:

```bash
git status --short
```

Review the diff:

```bash
git diff
```

For staged changes:

```bash
git diff --cached
```

Run tests before committing:

```bash
./gradlew test
```

## 2. Staging Changes

Stage only files that belong to the current task.

Example:

```bash
git add API.md openapi.yaml src/main/java/com/megabike/identity
```

Avoid staging unrelated local changes.

Check staged files:

```bash
git status --short
git diff --cached --stat
```

## 3. Commit Message Style

Use a short imperative subject line.

Good examples:

```text
Add auth login endpoint and API docs
Add identity security foundation
Fix Liquibase dev seed timestamps
```

Recommended format:

```text
Short subject line

Explain what changed.

Mention important implementation details.

Mention verification:
- ./gradlew test
```

Example:

```bash
git commit -m "Add auth login endpoint and API docs" \
  -m "Implement AuthController, AuthService, login DTOs, and invalid-credentials handling." \
  -m "Add JwtService for signed HS256 access-token creation." \
  -m "Document the API with API.md, openapi.yaml, and a Postman collection." \
  -m "Tests: ./gradlew test"
```

## 4. Pushing

Push the current branch to origin:

```bash
git push origin master
```

If working on a feature branch:

```bash
git push origin <branch-name>
```

## 5. After Pushing

Confirm the latest commit:

```bash
git log -1 --oneline
```

Confirm the working tree is clean:

```bash
git status --short
```

No output from `git status --short` means there are no uncommitted changes.

## 6. Current Repository Convention

Current main branch:

```text
master
```

Remote:

```text
origin
```

Typical push command:

```bash
git push origin master
```
