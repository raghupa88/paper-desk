# CI workflow

`github-actions-ci.yml` is a ready-to-use GitHub Actions workflow (backend
`mvn test` on Temurin 21; frontend `npm ci`, typecheck, tests, production
build on Node 22).

It lives here rather than in `.github/workflows/` because the credentials
used to push this branch do not have the `workflow` scope. To activate CI,
move it (one commit, e.g. via the GitHub web UI):

    .github/workflows/ci.yml
