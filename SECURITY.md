# Security policy

Please do not open a public issue for a security problem.

Report it privately from this repository's **Security** tab with **Report a vulnerability**. Include the affected
version or commit, how to reproduce it, and what an attacker could do with it.

Only the latest commit on `main` is supported.

## Running OrderPilot safely

- Deploy with the `prod` profile, which the backend image uses by default. It refuses to start with a missing or
  placeholder `JWT_SECRET`, default database or RabbitMQ passwords, or demo data enabled.
- `docker-compose.yml` is a local demo. It seeds accounts with a password published in this repository, so never
  expose it to the internet.
