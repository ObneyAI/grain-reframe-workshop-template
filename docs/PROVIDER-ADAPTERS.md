# Provider adapters

The starter keeps application decisions separate from vendor mechanics:

```text
domain module → small interface → selected adapter → external provider
                       ↘ deterministic local/test adapter
```

Application code receives adapters through the Grain context and calls only these interfaces:

- `app.email.interface` — send a normalized message.
- `app.file-store.interface` — put, get, delete, and locate an object.
- `app.url-presigner.interface` — create upload and download grants.
- `app.crypto.interface` — encrypt and decrypt a versioned envelope.
- `app.webhooks.interface` — verify, claim, process, audit, and replay deliveries.

## Adding another provider

1. Keep the existing interface provider-neutral; do not expose an SDK object or vendor response.
2. Put translation, credentials, timeouts, and provider errors inside a new adapter.
3. Select the adapter in validated configuration and wire it once in `app.web-api.core`.
4. Exercise domain behavior with the deterministic adapter. Add focused translation tests for the vendor adapter.
5. Redact credentials, message bodies, signed URLs, signatures, and protected values from logs.

For example, an SMS capability should define what the application needs (`send`) and supply both a Twilio
adapter and a capturing/failure-simulating adapter. Domain modules should never build Twilio form bodies.

## Webhook route recipe

1. Capture the exact request bytes with `app.webhooks.interface/capture-raw-body` before parsing.
2. Use the vendor's signature verifier, or the included timestamp-aware HMAC-SHA256 verifier.
3. Extract the provider event ID and call `receive!`; duplicate IDs do not call the handler again.
4. Return success only after the receipt is claimed. Keep domain work idempotent as an additional safeguard.
5. Replace the process-local receipt storage with durable storage before production. Encrypt retained payloads
   when they contain sensitive data and apply a retention policy.

## File ownership rules

Use tenant- and domain-owned keys such as `tenants/<tenant-id>/contacts/<contact-id>/documents/<file-id>`.
Authorize before issuing a presigned URL. Keep file metadata in the domain/event store and bytes in object
storage. A clone that accepts untrusted files must also define size/type limits, scanning or quarantine,
retention, deletion, and orphan cleanup.
