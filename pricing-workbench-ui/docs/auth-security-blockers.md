# Auth Security Blockers

## Implemented local guardrail

- Password login and registration now require a tenant-context auth base URL (`VITE_TENANT_CONTEXT_AUTH_BASE_URL`, with legacy `VITE_TENANT_CONTEXT_API_BASE_URL` still accepted for compatibility).
- The UI refuses to submit credential-bearing requests when the configured tenant auth base matches `VITE_BFF_API_BASE_URL`.
- Non-credential session checks can still use the configured tenant auth/API base or BFF base for compatibility, but raw credential submission is blocked without explicit tenant auth configuration.

## Remaining blocker

Full OIDC/PKCE or server-side BFF auth code exchange is not safely implementable from the current local context. It needs approved IdP configuration: issuer, client id, redirect URIs, scopes, cookie/session ownership, token exchange endpoint, logout behavior, and CSRF/session rotation requirements.

Until those values are supplied by approved configuration, do not invent IdP endpoints or route raw credentials through the BFF.
