import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const styles = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8')
const areaGuideSource = readFileSync(join(process.cwd(), 'src', 'pages', 'AreaGuideDrawer.tsx'), 'utf8')
const battleSource = readFileSync(join(process.cwd(), 'src', 'pages', 'BattlePage.tsx'), 'utf8')
const pokemonAreaSource = readFileSync(join(process.cwd(), 'src', 'pages', 'PokemonAreaMap.tsx'), 'utf8')

describe('screen layout containment', () => {
  it('keeps root titles left aligned when the header also has actions', () => {
    const rootRule = styles.match(/\.app-header\.app-header-root\s*\{([^}]*)\}/)?.[1]
    const actionRule = styles.match(/\.app-header:not\(\.app-header-root\):has\(\.header-actions\)\s*\{([^}]*)\}/)?.[1]

    expect(rootRule).toMatch(/grid-template-columns\s*:\s*12px minmax\(0, 1fr\) auto/)
    expect(actionRule).toMatch(/grid-template-columns\s*:\s*54px minmax\(0, 1fr\) auto/)
  })

  it('clips screen overflow so the declared content region owns scrolling', () => {
    const screenRule = styles.match(/\.screen\s*\{([^}]*)\}/)?.[1]

    expect(screenRule).toBeDefined()
    expect(screenRule).toMatch(/overflow\s*:\s*hidden/)
  })

  it('gives the capability page one bounded scrolling column', () => {
    const screenRule = styles.match(/\.capability-screen\s*\{([^}]*)\}/)?.[1]
    const contentRule = styles.match(/\.capability-content\s*\{([^}]*)\}/)?.[1]

    expect(screenRule).toMatch(/grid-template-rows\s*:\s*auto 1fr/)
    expect(contentRule).toMatch(/min-height\s*:\s*0/)
    expect(contentRule).toMatch(/overflow\s*:\s*auto/)
  })

  it('keeps Party Analysis bounded at 4:3 with one owned scrolling region', () => {
    const screenRule = styles.match(/\.party-analysis-screen\s*\{([^}]*)\}/)?.[1]
    const contentRule = styles.match(/\.party-analysis-content\s*\{([^}]*)\}/)?.[1]
    const sectionRule = styles.match(/\.party-analysis-section\s*\{([^}]*)\}/)?.[1]

    expect(screenRule).toMatch(/grid-template-rows\s*:\s*auto 1fr/)
    expect(contentRule).toMatch(/min-height\s*:\s*0/)
    expect(contentRule).toMatch(/overflow\s*:\s*auto/)
    expect(sectionRule).toMatch(/width\s*:\s*min\(980px, 100%\)/)
  })

  it('reserves the Map screen for a full black gesture stage and accessible fallback controls', () => {
    const screenRule = styles.match(/\.map-screen\s*\{([^}]*)\}/)?.[1]
    const stageRule = styles.match(/\.map-stage\s*\{([^}]*)\}/)?.[1]
    const controlRule = styles.match(/\.map-control\s*\{([^}]*)\}/)?.[1]

    expect(screenRule).toMatch(/grid-template-rows\s*:\s*auto 1fr/)
    expect(stageRule).toMatch(/touch-action\s*:\s*none/)
    expect(stageRule).toMatch(/overflow\s*:\s*hidden/)
    expect(stageRule).toMatch(/background\s*:\s*#000/)
    expect(controlRule).toMatch(/width\s*:\s*46px/)
    expect(controlRule).toMatch(/height\s*:\s*46px/)
    expect(styles).not.toContain('.map-navigation-row')
    expect(styles).not.toContain('[data-map-navigation-row]')
  })

  it('bounds the Area Guide over the map and gives long guide sections one windowed scroll region', () => {
    const drawerRule = styles.match(/\.area-guide-drawer\s*\{([^}]*)\}/)?.[1]
    const contentRule = styles.match(/\.area-guide-content\s*\{([^}]*)\}/)?.[1]
    const listRule = styles.match(/\.area-guide-windowed-list\.is-virtual\s*\{([^}]*)\}/)?.[1]

    expect(drawerRule).toMatch(/position\s*:\s*absolute/)
    expect(drawerRule).toMatch(/bottom\s*:\s*12px/)
    expect(drawerRule).toMatch(/overflow\s*:\s*hidden/)
    expect(contentRule).toMatch(/min-height\s*:\s*0/)
    expect(contentRule).toMatch(/overflow-y\s*:\s*auto/)
    expect(listRule).toMatch(/overflow-y\s*:\s*auto/)
  })

  it('does not give the Area Guide its own polling or animation loop', () => {
    expect(areaGuideSource).not.toMatch(/setInterval|setTimeout|requestAnimationFrame|fetch\s*\(/)
    expect(areaGuideSource).toContain("console.debug(JSON.stringify({ event: 'area-guide-render', renderMillis, retainedItems }))")
  })

  it('keeps the damage forecast inside the existing Battle scroll owner and theme surfaces', () => {
    const gridRule = styles.match(/\.damage-forecast-grid\s*\{([^}]*)\}/)?.[1]

    expect(styles).toMatch(/\.detail-content,\s*\.battle-content[^{}]*\{[^}]*overflow\s*:\s*auto/)
    expect(gridRule).toMatch(/grid-template-columns\s*:\s*repeat\(4, minmax\(0, 1fr\)\)/)
    expect(styles).toContain('[data-theme="game"][data-contrast="normal"] .damage-forecast')
    expect(battleSource).not.toMatch(/setInterval|setTimeout|requestAnimationFrame|fetch\s*\(/)
  })

  it('places Pokédex identity and two rows of detail tabs in one compact header band', () => {
    const screenRule = styles.match(/\.detail-screen\s*\{([^}]*)\}/)?.[1]
    const identityRule = styles.match(/\.identity-card\s*\{([^}]*)\}/)?.[1]
    const tabsRule = styles.match(/\.identity-card > \.segmented\s*\{([^}]*)\}/)?.[1]
    const firstRowRule = styles.match(/\.identity-card > \.segmented button:nth-child\(-n \+ 3\)\s*\{([^}]*)\}/)?.[1]
    const secondRowRule = styles.match(/\.identity-card > \.segmented button:nth-child\(n \+ 4\)\s*\{([^}]*)\}/)?.[1]

    expect(screenRule).toMatch(/grid-template-rows\s*:\s*auto 132px minmax\(0, 1fr\)/)
    expect(identityRule).toMatch(/grid-template-columns\s*:\s*108px minmax\(150px, \.7fr\) minmax\(280px, 2fr\)/)
    expect(tabsRule).toMatch(/grid-template-columns\s*:\s*repeat\(6, minmax\(0, 1fr\)\)/)
    expect(tabsRule).toMatch(/grid-template-rows\s*:\s*repeat\(2, minmax\(0, 1fr\)\)/)
    expect(firstRowRule).toMatch(/grid-column\s*:\s*span 2/)
    expect(secondRowRule).toMatch(/grid-column\s*:\s*span 3/)
  })

  it('keeps the Pokédex Area empty state compact instead of inheriting full-page empty-state spacing', () => {
    const areaRule = styles.match(/\.pokemon-area-empty\s*\{([^}]*)\}/)?.[1]

    expect(pokemonAreaSource).toContain('class="pokemon-area-empty"')
    expect(pokemonAreaSource).not.toContain('class="pokemon-area-empty empty-state"')
    expect(areaRule).toMatch(/align-self\s*:\s*start/)
    expect(areaRule).toMatch(/max-width\s*:\s*none/)
  })

  it('fits the rarity panel inside the remaining Battle viewport without making it scroll', () => {
    const contentRule = styles.match(/\.battle-content:has\(> \.rarity-card\)\s*\{([^}]*)\}/)?.[1]
    const rarityRule = styles.match(/\.rarity-card\s*\{([^}]*)\}/)?.[1]

    expect(contentRule).toMatch(/display\s*:\s*grid/)
    expect(contentRule).toMatch(/overflow\s*:\s*hidden/)
    expect(contentRule).toMatch(/padding\s*:\s*10px 12px/)
    expect(rarityRule).toMatch(/min-height\s*:\s*0/)
    expect(rarityRule).toMatch(/height\s*:\s*100%/)
    expect(rarityRule).toMatch(/margin\s*:\s*0/)
  })

  it('lets a single specimen fill the page and preserves its full labels', () => {
    const gridRule = styles.match(/\.specimens-grid\s*\{([^}]*)\}/)?.[1]
    const cardRule = styles.match(/\.specimen-card\s*\{([^}]*)\}/)?.[1]
    const metaRule = styles.match(/\.specimen-card-meta\s*\{([^}]*)\}/)?.[1]

    expect(gridRule).toMatch(/grid-template-columns\s*:\s*repeat\(auto-fit, minmax\(min\(440px, 100%\), 1fr\)\)/)
    expect(cardRule).toMatch(/grid-template-areas\s*:\s*"sprite copy" "sprite meta"/)
    expect(metaRule).toMatch(/display\s*:\s*flex/)
    expect(metaRule).toMatch(/justify-content\s*:\s*space-between/)
  })

  it('keeps Trainer destination switching in compact header controls', () => {
    const trainerRule = styles.match(/\.trainer-screen\s*\{([^}]*)\}/)?.[1]
    const switcherRule = styles.match(/\.trainer-destination-switcher\s*\{([^}]*)\}/)?.[1]
    const actionRule = styles.match(/\.trainer-destination-action\s*\{([^}]*)\}/)?.[1]

    expect(trainerRule).toMatch(/grid-template-rows\s*:\s*auto minmax\(0, 1fr\)/)
    expect(switcherRule).toMatch(/grid-auto-columns\s*:\s*48px/)
    expect(actionRule).toMatch(/min-width\s*:\s*48px/)
    expect(actionRule).toMatch(/min-height\s*:\s*48px/)
    expect(styles).not.toContain('.trainer-destination-tabs')
  })

  it('uses one shared token-backed grid surface for every new non-map route', () => {
    const rootRule = styles.match(/:root\s*\{([^}]*)\}/)?.[1]
    const sharedRule = styles.match(/\.trainer-card-content,\s*\.trainer-progress-content,\s*\.party-analysis-content,\s*\.specimens-content\s*\{([^}]*)\}/)?.[1]

    expect(rootRule).toMatch(/--ui-grid-line\s*:/)
    expect(rootRule).toMatch(/--ui-raised-shadow\s*:/)
    expect(sharedRule).toMatch(/background-color\s*:\s*var\(--paper-deep\)/)
    expect(sharedRule).toMatch(/background-image\s*:/)
  })

  it('keeps new route surfaces theme-driven outside GAME mode too', () => {
    const trainerCardRule = styles.match(/\.trainer-card-content\s*\{([^}]*)\}/)?.[1]
    const damageRule = styles.match(/\.damage-forecast\s*\{([^}]*)\}/)?.[1]
    const specimenRule = styles.match(/\.specimen-card\s*\{([^}]*)\}/)?.[1]

    expect(trainerCardRule).not.toMatch(/#[0-9a-f]{3,8}/i)
    expect(damageRule).toMatch(/background\s*:\s*var\(--paper-deep\)/)
    expect(damageRule).not.toMatch(/#[0-9a-f]{3,8}/i)
    expect(specimenRule).toMatch(/box-shadow\s*:.*var\(--ui-raised-shadow\)/)
  })

  it('enforces the physical text floor on the smallest new-route labels', () => {
    expect(styles).toContain('--ui-min-text: 11.2px')
    expect(styles).toMatch(/\.challenge-card > div:first-child span[^{}]*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.6rem\)/)
    expect(styles).toMatch(/\.damage-forecast-grid small[^{}]*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.64em\)/)
    expect(styles).toMatch(/\.area-guide-exits > small[^{}]*\{[^}]*font\s*:\s*900 max\(var\(--ui-min-text\),\s*\.58rem\)/)
  })
})
