# Article AI Preview Implementation Plan

## Decision

Article AI generation uses a preview-first flow:

1. The user fills structured generation inputs in the article creation page.
2. The frontend calls `POST /api/content/articles/ai-draft/preview`.
3. The backend calls the configured LLM and returns generated Markdown plus trace metadata, but does not create `article_draft`.
4. The frontend parses the Markdown, fills the editable article form, and shows parser status.
5. The user edits the draft and submits through the existing manual create path.

This keeps AI output out of the review queue until an operator explicitly submits it.

## Required API Changes

### Preview Generate

`POST /api/content/articles/ai-draft/preview`

Request fields:

- `projectId`
- `articleType`
- `contentStyle`
- `tone`
- `length`
- `topic`
- `extraPrompt`
- `referenceMaterials`
- `modelPlatformCode`
- `modelId`

Response fields:

- `title`
- `contentMarkdown`
- `promptSnapshot`
- `inputSnapshot`
- `modelResponseSnapshot`
- `modelPlatformCode`
- `modelId`
- `modelName`

### Manual Create Metadata

The first implementation must extend manual submit with:

- `source`: defaults to `manual`; AI-assisted submissions use `ai_preview`.
- `aiMetadata`: nullable object containing `inputSnapshot`, `promptSnapshot`, `modelResponseSnapshot`, `modelPlatformCode`, `modelId`, and `modelName`.

Manual mode is unaffected because the new fields are optional. AI-assisted mode must pass metadata on submit so the original model response is not lost after user edits.

## Markdown Parsing

The frontend parser must classify generated Markdown into three statuses:

- `success`: title and at least one section are parsed.
- `partial`: title or sections are missing, but usable content exists.
- `failed`: no reliable title or section structure can be parsed.

UI behavior:

- `success`: fill title and sections without warning.
- `partial`: fill what can be parsed and show a visible warning explaining what was missing.
- `failed`: keep the raw Markdown in source mode and show a visible warning that structure parsing failed.

The parser must not silently discard model output.

## Timeout Requirements

Target chain timeout for `ai-draft/preview`: at least 120 seconds from frontend to model response.

Current confirmed state after this implementation:

- Production Nginx `proxy_read_timeout`: `120s`.
- Vite dev proxy `timeout` and `proxyTimeout`: `120000ms`.
- Frontend preview request timeout: `120000ms`; other requests keep the axios default.
- Common `LlmModelConfig.MAX_REQUEST_TIMEOUT_MS`: `60000ms`; article preview uses `LONG_FORM_MAX_REQUEST_TIMEOUT_MS`: `180000ms`.
- `ai_platform_config.timeout_ms` schema default: `60000ms`.

Implementation must align these for the preview path:

- frontend preview request timeout: at least `120000ms`;
- Vite proxy timeout: at least `120000ms`;
- LLM request timeout cap/default for article preview: at least `120000ms`;
- model config should honor configured timeout where possible.

Frontend UX:

- Show normal loading immediately.
- At 90 seconds, show a "still generating" message while keeping the request alive.
- On failure or timeout, preserve all user inputs and allow regeneration.

If a deployment gateway cannot be configured to 120 seconds, the fallback is to move preview generation to an asynchronous job with polling or SSE before release. Do not ship a blocking preview endpoint behind a shorter gateway timeout.
