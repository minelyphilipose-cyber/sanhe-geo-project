# Local TODO

## Phase 1 master branch was advanced to P1.4-b

Priority: Low. Workflow-only event, no functional impact.

Facts:
- `origin/master` current HEAD = `73baff189a6de759f1f93fe94388a59a5237e098` (P1.4-b).
- `feature/phase1-multichannel-c1` HEAD = `73baff189a6de759f1f93fe94388a59a5237e098`.
- Per the Phase 1 kickoff agreement, `master` should have stayed at the Phase 0 commit `04529435` until Phase 1 passed overall review.
- In practice, `master` has already been advanced, likely by the previous Codex session in Cursor IDE.

Impact:
- The code content is acceptable because all commits were reviewed by Claude.
- `master` is no longer the stable baseline branch.
- Follow-up Phase 1 strategy is adjusted: continue pushing to `feature/phase1-multichannel-c1`; do not actively push `master`. After Phase 1 completes, `master` is already in a fast-forward-synced state and needs no extra merge.

Response for Phase 1:
- Accept the current state and do not force-push reset `master`.
- After Phase 1 completes, if wangheng decides to preserve the "master = stable baseline" meaning, clean it up based on a business decision. Options may include force-resetting `master` to `04529435` and re-merging, or permanently accepting that `master` advances in sync with the feature branch.

### Tech debt #4: OfficialCmsSiteAdapter framework-row matching (closed by Phase 2A)

**Status:** Closed in Phase 2A. The framework-row matching mechanism on
`OfficialCmsSiteAdapter` is preserved with the cold-stored legacy C1 code path.
The new C1' brand GEO site channel does not require any framework lookup; its
template is hardcoded in `BrandGeoSiteAdapter`. This debt will only become
relevant again if legacy C1 is restored.

Owner: wangheng

### Tech debt #7: BrandOfficialSiteController.requireSiteAndAccess bypasses service layer

`BrandOfficialSiteController.requireSiteAndAccess` uses a direct mapper query plus inline permission check,
which is functionally equivalent to `BrandOfficialSiteService.getSite(id)`, but duplicates the access pattern.

The `resourceTag` also differs: the controller uses "brand_official_site", while the service uses "brand".
The controller tag is more precise; when this is unified, the service should follow the controller.

This does not block functionality. Recommended cleanup: handle it together with service-layer contract cleanup in Phase 2A.

Owner: wangheng

### Tech debt #8: Legacy C1 failure finalization is incomplete

`ContentDistributionService.distributeToBrandOfficialSite` reserves monthly quota before calling
`OfficialCmsSiteAdapter`, but a failed publish does not refund the reserved quota. Its failure
finalizer also does not persist `request_payload` / `response_payload`, which makes failed legacy
C1 attempts harder to audit.

This is intentionally not fixed while legacy C1 is cold-stored. Fix it together with #7 when the
customer-official-CMS entry point is restored.

Owner: wangheng

### Tech debt #9: Legacy C1 REQUIRES_NEW self-invocation does not take effect

`beginAttemptForBrandOfficialSite` and `finalizeAttemptForBrandOfficialSite` are annotated with
`REQUIRES_NEW`, but they are invoked from methods on the same Spring bean. The calls bypass the
transaction proxy, so the intended transaction boundary is not actually applied.

The new C1' brand GEO site channel uses `ProjectPublishQuotaService` as a real Spring service for
quota reservation/refund. Apply the same extraction to legacy C1 if that entry point is restored.

Owner: wangheng
