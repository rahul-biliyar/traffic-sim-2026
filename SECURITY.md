# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub Issues.**

Use [GitHub's private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) to submit a report directly to the maintainers. Reports are acknowledged within 7 days.

### Scope

This project runs a Micronaut WebSocket server (port 8000) intended for local or self-hosted use. The relevant attack surface includes:

- WebSocket message parsing and deserialization
- Session state management
- Static resource serving

### Out of scope

- Vulnerabilities in third-party dependencies (report those upstream: Micronaut, Three.js, Netty)
- Issues only reproducible on unreleased or unsupported versions
