# Security policy

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Contact the repository
owner privately through GitHub's **Report a vulnerability** feature.

## Sensitive data

- Never commit signing keys, API keys, access tokens, model credentials, or private user data.
- Offline OCR and on-device captions do not need a cloud API key.
- OCR model downloads use HTTPS and are stored in the app's private files directory.
- Captured images are written to the app cache only for processing and are deleted after the OCR attempt.
- Do not add shell tracing (\`set -x\`) or print environment variables in CI jobs.

## Safety

Glazouh is an assistive aid, not a certified medical device or traffic-safety system.
OCR can misread medication names, strengths, expiry dates, warning labels, banknotes, and signs.
Always confirm critical information with a trusted person or authoritative source.
Never use an unverified recognition result to decide a medication dose or whether it is safe to cross a road.
