import { describe, expect, it } from 'vitest'
import { FILL_PROFILES } from './fillProfiles'

describe('fill profiles', () => {
  it('keeps publish selectors explicit without generic submit fallback', () => {
    for (const profile of Object.values(FILL_PROFILES)) {
      expect(profile.publishButtonSelectors).toEqual(['[data-geo-publish]'])
      expect(profile.publishButtonSelectors).not.toContain('button[type="submit"]')
      expect(profile.publishButtonSelectors).not.toContain('button.publish')
      expect(profile.publishButtonSelectors).not.toContain('.publish-button')
    }
  })
})
