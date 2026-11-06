# Smart-ID trusted CA certificates (public, safe to commit)

Downloaded on 2026-11-06 from https://www.skidsolutions.eu/resources/certificates/ and checked with
`openssl verify` (each intermediate chains to its root). The backend trusts **only** these certificates
for Smart-ID user certificates, never the JVM trust store. See docs/auth.md.

- `demo/`: SK's TEST CAs, for SMARTID_ENV=demo
- `production/`: SK's real CAs, for SMARTID_ENV=production

When SK publishes a new issuing CA, add its PEM to `<env>/intermediates/`.
