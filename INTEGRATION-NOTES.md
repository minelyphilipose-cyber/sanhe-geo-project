# Sprint 2 W3 Integration Notes

Date: 2026-05-07
Branch: `codex/sprint2-w3-integration-notes`

## Scope

This note records Sprint 2 end-to-end integration findings only. No code changes were made on this branch.

Target path:

1. Manual article create -> submit review.
2. Different operator reviews and approves.
3. Create self-media distribution task.
4. Extension bind -> task list -> cookie capture -> fill -> ack -> heartbeat -> published.
5. Audit log review.
6. AI draft path -> review -> distribution -> extension fill -> published.

## ISSUE-1: `master` Does Not Contain B5a/B5b Extension Lifecycle Code

**路径**：Before running the extension end-to-end path, synced `master` with `git switch master && git pull --ff-only`, then inspected the code and branch containment.

**预期**：`master` should contain B5a and B5b, including `geo-extension/src/service-worker/fillFlow.ts`, `taskLifecycle.ts`, and content-script publish listener code, because B5b was reported as merged.

**实际**：`origin/master` is at `22613144 D4b: address AI draft review feedback`. B5a/B5b commits are only on `origin/codex/sprint2-b5a-fill-and-inject` and `origin/codex/sprint2-b5b-state-progression`. On `master`, `geo-extension/src/service-worker/fillFlow.ts` and `geo-extension/src/service-worker/taskLifecycle.ts` do not exist, so the extension fill/heartbeat/published path cannot be run from the integration baseline.

**严重性**：Blocker

**怀疑根因**：B5a/B5b review branches were pushed and acknowledged, but have not actually been fast-forward merged into `master` in the remote repository.

## ISSUE-2: Real Platform Credential Preconditions Are Not Available In This Workspace

**路径**：Attempted to prepare the cookie capture and real editor fill checks for Toutiao/Zhihu.

**预期**：The integration runner needs an operator account, supported brand/account rows, and a real logged-in Toutiao or Zhihu browser session to validate cookie completeness, editor login state, selector matching, and publish button click capture.

**实际**：This workspace has no provided real Toutiao/Zhihu login credentials or authenticated browser session. Without those, the following checklist items cannot be validated manually: whether injected cookies preserve login state, whether platform required-cookie lists are sufficient, whether actual editor DOM selectors match, whether React/Vue controlled inputs accept programmatic value updates, and whether real publish buttons match `publishButtonSelectors`.

**严重性**：Major

**怀疑根因**：Integration depends on external platform accounts and an interactive browser profile. These are environment prerequisites, not code-level failures.

## Not Executed Due To ISSUE-1

- Manual article creation/review distribution to extension task.
- Extension task list -> cookie capture -> fill token issue/consume.
- Editor tab auto-fill and ack.
- Heartbeat polling after ack.
- Manual publish click capture and `/published` state progression.
- Audit log chain verification.
- AI draft end-to-end path through review/distribution/fill/published.

## Static Checks Performed

- Confirmed `origin/master` does not contain B5a/B5b files.
- Confirmed B5a and B5b branches exist remotely:
  - `origin/codex/sprint2-b5a-fill-and-inject`
  - `origin/codex/sprint2-b5b-state-progression`

