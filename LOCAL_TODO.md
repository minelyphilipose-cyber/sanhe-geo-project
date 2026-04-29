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

### Tech debt #7: BrandOfficialSiteController.requireSiteAndAccess bypasses service layer

`BrandOfficialSiteController.requireSiteAndAccess` uses a direct mapper query plus inline permission check,
which is functionally equivalent to `BrandOfficialSiteService.getSite(id)`, but duplicates the access pattern.

The `resourceTag` also differs: the controller uses "brand_official_site", while the service uses "brand".
The controller tag is more precise; when this is unified, the service should follow the controller.

This does not block functionality. Recommended cleanup: handle it together with service-layer contract cleanup in Phase 2A.

Owner: wangheng
