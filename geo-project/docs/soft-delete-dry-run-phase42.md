# Soft Delete Dry Run - Phase 42

This report covers the current soft-delete migration impact for `company`, `brand`, and `project`.

## Unique Index Findings

| Table | Existing unique/index | Risk after soft delete | Suggested Phase 2 action |
|---|---|---|---|
| `company` | no unique company-name key found | no unique-index blocker | Keep `idx_company_deleted_at`; add query filters. |
| `brand` | `uk_brand_company_slug (company_id, brand_slug)` | Soft-deleted brand still blocks slug reuse. | Replace with active-only uniqueness via generated column, e.g. `active_slug_flag` = `IF(deleted_at IS NULL, 1, NULL)`, unique `(company_id, brand_slug, active_slug_flag)`. |
| `brand` | `uk_brand_geo_site_code (geo_site_code)` | Soft-deleted brand still blocks geo site code reuse. | Replace with active-only uniqueness via generated column, e.g. `active_geo_site_flag` = `IF(deleted_at IS NULL, 1, NULL)`, unique `(geo_site_code, active_geo_site_flag)`. |
| `project` | `uk_project_code (project_code)` | Project code should remain globally unique, including deleted rows. | Keep as-is. Do not allow code reuse. |

Do not use `(company_id, brand_slug, deleted_at)` as the replacement unique key in MySQL, because
multiple active rows with `deleted_at IS NULL` can coexist under a nullable unique column.

## Project Restore Path Check

Static search found no active backend or frontend restore/undelete path for `project.deleted_at`.
The only project soft-delete write path currently sets `deletedAt` in `ProjectService.delete()`.
There is no API or UI flow that clears `Project.deletedAt`.

Conclusion: keep `uk_project_code (project_code)` unchanged. A deleted project cannot be restored
through the application today, and project code reuse should remain forbidden.

## Query Coverage Findings

Already covered in this phase:

- `CompanyService.page()` filters `Company.deletedAt IS NULL`.
- `CompanyService.requireCompany()` rejects deleted companies.
- `BrandService.page()` filters `Brand.deletedAt IS NULL`.
- `BrandService.requireBrand()` rejects deleted brands.
- `ProjectService.page()` filters `Project.deletedAt IS NULL`.
- `ProjectService.requireProject()` rejects deleted projects.

Phase 2 coverage added:

- `V97__phase42_soft_delete_active_brand_uniques.sql` replaces brand slug and GEO site unique keys with active-only generated-column unique keys.
- `BrandService.create()` duplicate `brand_slug` check ignores deleted brands.
- `BrandService.update()` duplicate `brand_slug` check ignores deleted brands.
- `BrandService.applyGeoSiteFields()` duplicate `geo_site_code` check ignores deleted brands.
- `CompanyService.delete()` brand-count check counts only non-deleted brands.
- `BrandService.delete()` project-count check counts only non-deleted projects.
- `ProjectService.validateCompany()` rejects deleted companies.
- `ProjectService.validateCompanyBrand()` rejects deleted brands.
- `ProjectService.resolveBrandName()` rejects deleted brands.
- `ProjectService.ensureSalesProjectAccess()` rejects deleted companies.
- `CurrentUserService.ensureBrandAccess()` rejects deleted brands and deleted companies.
- `BrandProfileService` and `BrandStatementService` reject deleted brands/companies.
- `ContentArticleService`, `ContentDistributionService`, `QuestionPoolService`, `ReportService`, and `ProjectDashboardService` reject deleted projects on their read/write entry points.
- `DashboardService` filters deleted companies/projects in overview counts, pending lists, stage distribution, report trend partner scoping, and helper project-id loading.
- Report keyword project lookup and report project-name attachment ignore deleted projects.

Remaining follow-up gaps:

- Some historical report subject fallback code may still display soft-deleted company/brand names if the project snapshot did not already contain them. This is acceptable for historical report rendering but should not be used for live list filtering.
- Brand child-table list/detail endpoints were checked after Phase 3. `BrandMaterial` and
  `BrandProfileVersion` queries enter through `BrandProfileService.requireAccessibleBrand()`, which
  rejects deleted brands and deleted companies before child rows are queried.
- Raw SQL or XML mapper queries, if added later, must be reviewed separately because they do not inherit these service-level filters.

## Suggested Migration Shape

Phase 2 migration should:

1. Emit diagnostic precheck result sets for active duplicate `brand_slug` and `geo_site_code` groups.
2. Drop `uk_brand_company_slug` and `uk_brand_geo_site_code`.
3. Add generated active-only flag columns.
4. Add active-only unique keys.
5. Keep `project_code` uniqueness unchanged.

Existing unique keys already prevent active duplicates before this migration. The replacement unique
keys are still the enforcement point: if unexpected active duplicates exist, the diagnostic SELECTs
will show the conflicting groups and adding the new unique keys will fail migration. The
implementation should be tested against MySQL behavior for nullable unique columns before rollout.
