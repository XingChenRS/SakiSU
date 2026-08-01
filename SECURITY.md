# Reporting Security Issues

The SakiSU maintainers take security reports seriously and appreciate responsible disclosure.

Please report a vulnerability privately through the GitHub Security Advisory [Report a vulnerability](https://github.com/XingChenRS/SakiSU/security/advisories/new) form. Include affected versions, reproduction steps, impact, and any proposed mitigation when available.

Do not open a public issue for an unpatched vulnerability. The maintainers will acknowledge the report, coordinate follow-up questions, and keep the reporter informed as a fix and disclosure plan are prepared.

## Production signing

Store `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` only as secrets in the protected GitHub Environment named `production-signing`. Restrict that Environment to `main` and `v*` tags, enable required reviewers where practical, and do not duplicate these values as repository-level secrets.

Normal branch and pull-request builds use an isolated one-run certificate. Production refs also use that isolated certificate for intermediate Gradle artifacts; the protected Environment is opened only for the final repack, where the certificate is checked against `kernel/manager/manager_sign.h` before any APK is signed.
