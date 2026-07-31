# SmartAI Core API

Spring Boot backend for the recruitment agent. The first implemented business slice is the
natural-language requirement draft flow:

- `POST /api/core/v1/requirement-drafts`
- `GET /api/core/v1/requirement-drafts/{draftId}`
- `PATCH /api/core/v1/requirement-drafts/{draftId}`
- `POST /api/core/v1/requirement-drafts/{draftId}/convert`
- `GET /api/core/v1/recruitment-tasks/{taskId}`

The second implemented slice covers the G2 position-plan lifecycle:

- `POST /api/core/v1/recruitment-tasks/{taskId}/position-plan/generations`
- `GET /api/core/v1/recruitment-tasks/{taskId}/position-plan`
- `GET/PATCH /api/core/v1/position-plan-versions/{planVersionId}`
- `POST /api/core/v1/position-plan-versions/{planVersionId}/review-requests`
- `POST /api/core/v1/human-checkpoints/{checkpointId}/decisions`
- `GET /api/core/v1/agent-runs/{agentRunId}`

G2 currently uses `DETERMINISTIC_DEMO`; it does not call an LLM or knowledge retrieval service.

The third implemented slice covers deterministic candidate normalization and matching:

- `POST /api/core/v1/candidate-inputs`
- `POST /api/core/v1/recruitment-tasks/{taskId}/match-runs`
- `GET /api/core/v1/match-runs/{matchRunId}`
- `GET /api/core/v1/match-runs/{matchRunId}/results`
- `GET /api/core/v1/match-results/{matchResultId}`
- `GET /api/core/v1/recruitment-tasks/{taskId}/task-candidates`

The matching slice only accepts an approved G2 position plan and scorecard. It applies hard filters
before deterministic weighted scoring and records resume-version evidence for every non-zero criterion.
Candidate inputs, normalized resume content, idempotent command responses, and match results are
encrypted at rest. Responses and audit events identify the generator as `DETERMINISTIC_RULES`.
This slice does not currently call an LLM, RAG service, or vector database.

`POST` requires a UUID `Idempotency-Key` header. In the `local` profile, calls use the fixed demo
tenant unless `X-SmartAI-Demo-Tenant-Id` is supplied. Demo tenant headers are never trusted outside
the `local` profile. The `production` profile continues to reject anonymous business requests.

PATCH and convert also require `If-Match`. Draft responses include `X-SmartAI-Input-Hash`; the UI
must submit that exact hash in `confirmation.inputHash` when converting the displayed draft. A
changed version, expired draft, changed confirmation input, or second conversion is rejected.

Raw HR input is encrypted with AES-256-GCM before persistence. Set the following production secret
to a Base64-encoded 32-byte key:

```text
SMARTAI_REQUIREMENT_DRAFT_ENCRYPTION_KEY
```

Run locally:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Verify:

```powershell
.\mvnw.cmd test --batch-mode --no-transfer-progress
```
