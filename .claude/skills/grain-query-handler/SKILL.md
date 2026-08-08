---
name: grain-query-handler
description: Implement data-returning Grain queries consumed by the Re-frame frontend.
---

# Grain query handler

Backend queries return serializable data through `{:query/result ...}`. They do not render HTML, own a
browser path, or reference UI namespaces.

Use `defquery :area name` with explicit `:authorized?` and `:grain.event-model/reads`. Read projections
through the owning component's read-model helpers, return only safe fields, and keep account-scoped data
protected on the server. Register the query schema and reconcile the event model. The frontend calls the
query through `app.api.interface/query` from a Re-frame effect and owns loading/error/empty state.
