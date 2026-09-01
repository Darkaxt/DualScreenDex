import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const styles = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8')
const componentsSource = readFileSync(join(process.cwd(), 'src', 'components.tsx'), 'utf8')
const areaGuideSource = readFileSync(join(process.cwd(), 'src', 'pages', 'AreaGuideDrawer.tsx'), 'utf8')
const battleSource = readFileSync(join(process.cwd(), 'src', 'pages', 'BattlePage.tsx'), 'utf8')
const pokemonAreaSource = readFileSync(join(process.cwd(), 'src', 'pages', 'PokemonAreaMap.tsx'), 'utf8')
const settingsSource = readFileSync(join(process.cwd(), 'src', 'pages', 'SettingsPage.tsx'), 'utf8')

describe('screen layout containment', () => {
  it('keeps header shortcuts readable against ROM-derived header colors', () => {
    const actionRule = styles.match(/\.header-action\s*\{([^}]*)\}/)?.[1]

    expect(actionRule).toMatch(/color\s*:\s*var\(--semantic-header-fg\)/)
    expect(actionRule).not.toMatch(/color\s*:\s*var\(--acid\)/)
  })

  it('uses the selected semantic palette for the current header destination', () => {
    const currentDestinationRule = styles.match(/\.header-destination-action\[aria-current="page"\]\s*\{([^}]*)\}/)?.[1]

    expect(currentDestinationRule).toMatch(/color\s*:\s*var\(--semantic-selected-fg\)/)
    expect(currentDestinationRule).toMatch(/background\s*:\s*var\(--semantic-selected-bg\)/)
    expect(styles).toContain('.header-action:hover:not([aria-current="page"])')
  })

  it('keeps the active Party Poké Ball upright on its red destination background', () => {
    expect(styles).toMatch(/\.header-destination-action\.party-action \.party-ball-body\s*\{[^}]*fill\s*:\s*currentColor/)
    expect(styles).toMatch(/\.header-destination-action\.party-action \.party-ball-upper\s*\{[^}]*fill\s*:\s*var\(--semantic-selected-bg\)/)
    expect(styles).toMatch(/\.header-destination-action\.party-action \.party-ball-button-ring\s*\{[^}]*fill\s*:\s*var\(--semantic-selected-bg\)/)
  })

  it('gives compact Pokédex cards enough height for identity and type labels', () => {
    const compactRowRule = styles.match(/\[data-density="compact"\] \.species-row\s*\{([^}]*)\}/)?.[1]

    expect(compactRowRule).toMatch(/min-height\s*:\s*76px/)
  })

  it('keeps the compact Party experience bar thick and visibly blue against gray', () => {
    const trackRule = styles.match(/\.party-exp-track\s*\{([^}]*)\}/)?.[1]
    const fillRule = styles.match(/\.party-exp-fill\s*\{([^}]*)\}/)?.[1]
    const compactRule = styles.match(/@media \(max-width: 650px\)[\s\S]*?\.party-exp-track\s*\{([^}]*)\}/)?.[1]

    expect(trackRule).toMatch(/border\s*:\s*1px solid #36495d/)
    expect(trackRule).toMatch(/background\s*:\s*#d8dde2/)
    expect(fillRule).toMatch(/linear-gradient\(90deg, #0b6fbe, #2ca7f0\)/)
    expect(compactRule).toMatch(/height\s*:\s*7px/)
  })

  it('keeps the Local player pulse behind the Accessibility preference', () => {
    expect(styles).toMatch(/\.map-marker\.is-current:not\(\.atlas-location-marker\):not\(\.map-player-marker\)/)
    expect(styles).toMatch(/\.map-player-marker\.is-high-visibility\s*\{[^}]*animation\s*:\s*current-map-point/)
    expect(styles).toMatch(/\.map-player-marker\.has-sprite img\s*\{[^}]*filter\s*:\s*none/)
    expect(styles).toMatch(/\.map-player-marker\.has-sprite\.is-high-visibility img\s*\{[^}]*drop-shadow/)
  })

  it('keeps Party card copy inside the full padded card height', () => {
    const copyRule = styles.match(/\.party-slot-copy\s*\{([^}]*)\}/)?.[1]

    expect(copyRule).toMatch(/height\s*:\s*100%/)
    expect(copyRule).toMatch(/align-self\s*:\s*stretch/)
  })

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

  it('keeps every Stage 2 map action at the touch floor while preserving small marker artwork', () => {
    const localPoi = styles.match(/\.map-poi-marker\s*\{([^}]*)\}/)?.[1]
    const atlasMarker = styles.match(/\.atlas-location-marker\s*\{([^}]*)\}/)?.[1]
    const localSceneAction = styles.match(/\.map-local-poi-label:is\(button\)\s*\{([^}]*)\}/)?.[1]
    const clusterHeader = styles.match(/\.map-poi-cluster-popover > header\s*\{([^}]*)\}/)?.[1]
    const clusterClose = styles.match(/\.map-poi-cluster-popover > header button\s*\{([^}]*)\}/)?.[1]
    const poiCardClose = styles.match(/\.map-poi-card > button\s*\{([^}]*)\}/)?.[1]
    const habitatMarker = styles.match(/\.pokemon-area-canvas > button:not\(\.pokemon-area-dex\)\s*\{([^}]*)\}/)?.[1]
    const habitatDex = styles.match(/\.pokemon-area-dex\s*\{([^}]*)\}/)?.[1]

    expect(localPoi).toMatch(/width\s*:\s*44px/)
    expect(localPoi).toMatch(/height\s*:\s*44px/)
    expect(atlasMarker).toMatch(/width\s*:\s*44px/)
    expect(atlasMarker).toMatch(/height\s*:\s*44px/)
    expect(localSceneAction).toMatch(/min-height\s*:\s*44px/)
    expect(styles).toMatch(/\.map-header-actions \.header-action\s*\{[^}]*min-width\s*:\s*46px/)
    expect(clusterHeader).toMatch(/min-height\s*:\s*45px/)
    expect(clusterClose).toMatch(/min-height\s*:\s*44px/)
    expect(poiCardClose).toMatch(/width\s*:\s*44px/)
    expect(poiCardClose).toMatch(/height\s*:\s*44px/)
    expect(habitatMarker).toMatch(/width\s*:\s*44px/)
    expect(habitatMarker).toMatch(/height\s*:\s*44px/)
    expect(habitatDex).toMatch(/width\s*:\s*44px/)
    expect(habitatDex).toMatch(/height\s*:\s*44px/)
    expect(styles).toMatch(/\.map-poi-symbol[^{}]*\{[^}]*width\s*:\s*24px[^}]*height\s*:\s*24px/)
    expect(styles).toMatch(/\.atlas-location-marker span[^{}]*\{[^}]*width\s*:\s*11px[^}]*height\s*:\s*11px/)
  })

  it('bounds the Area Guide over the map and gives long guide sections one windowed scroll region', () => {
    const drawerRule = styles.match(/\.area-guide-drawer\s*\{([^}]*)\}/)?.[1]
    const contentRule = styles.match(/\.area-guide-content\s*\{([^}]*)\}/)?.[1]
    const listRule = styles.match(/\.area-guide-windowed-list\.is-windowed\s*\{([^}]*)\}/)?.[1]

    expect(drawerRule).toMatch(/position\s*:\s*absolute/)
    expect(drawerRule).toMatch(/bottom\s*:\s*12px/)
    expect(drawerRule).toMatch(/overflow\s*:\s*hidden/)
    expect(contentRule).toMatch(/min-height\s*:\s*0/)
    expect(contentRule).toMatch(/overflow-y\s*:\s*auto/)
    expect(listRule).toMatch(/overflow\s*:\s*hidden/)
    expect(listRule).not.toMatch(/overflow-y\s*:\s*auto/)
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

  it('allocates the complete Trainer destination switcher inside the compact header', () => {
    const trainerRule = styles.match(/\.trainer-screen\s*\{([^}]*)\}/)?.[1]
    const hostRule = styles.match(/\.header-actions:has\(> \.trainer-destination-switcher\)\s*\{([^}]*)\}/)?.[1]
    const switcherRule = styles.match(/\.trainer-destination-switcher\s*\{([^}]*)\}/)?.[1]
    const actionRule = styles.match(/\.trainer-destination-action\s*\{([^}]*)\}/)?.[1]

    expect(trainerRule).toMatch(/grid-template-rows\s*:\s*auto minmax\(0, 1fr\)/)
    expect(hostRule).toMatch(/grid-auto-columns\s*:\s*auto/)
    expect(switcherRule).toMatch(/width\s*:\s*96px/)
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

  it('keeps Stage 1 Settings and Setup actions at the touch floor', () => {
    const settingsUpload = styles.match(/\.settings-upload\s*\{([^}]*)\}/)?.[1]
    const retroArchAction = styles.match(/\.retroarch-setting button\s*\{([^}]*)\}/)?.[1]
    const setupAction = styles.match(/\.setup-action\s*\{([^}]*)\}/)?.[1]
    const displayModeAction = styles.match(/\.display-mode > a\s*\{([^}]*)\}/)?.[1]
    const capabilityAction = styles.match(/\.capability-actions button, \.capability-error button\s*\{([^}]*)\}/)?.[1]

    expect(settingsUpload).toMatch(/min-height\s*:\s*44px/)
    expect(retroArchAction).toMatch(/min-height\s*:\s*44px/)
    expect(setupAction).toMatch(/min-height\s*:\s*44px/)
    expect(displayModeAction).toMatch(/min-height\s*:\s*44px/)
    expect(capabilityAction).toMatch(/min-height\s*:\s*44px/)
  })

  it('keeps Settings categories, save choices, and toggles touch and keyboard visible', () => {
    const categoryRule = styles.match(/\.settings-category-row\s*\{([^}]*)\}/)?.[1]
    const saveRule = styles.match(/\.save-candidates button\s*\{([^}]*)\}/)?.[1]
    const toggleFocusRule = styles.match(/\.toggle-row input:focus-visible \+ i\s*\{([^}]*)\}/)?.[1]

    expect(categoryRule).toMatch(/min-height\s*:\s*52px/)
    expect(styles).toMatch(/\.settings-category-row small\s*\{[^}]*color\s*:\s*var\(--semantic-surface-fg\)/)
    expect(saveRule).toMatch(/min-height\s*:\s*44px/)
    expect(toggleFocusRule).toMatch(/outline\s*:\s*3px solid var\(--semantic-selected-border\)/)
    expect(settingsSource).toContain('class="primary-action"')
    expect(settingsSource).toContain('class="diagnostic-action"')
    expect(settingsSource).toContain('class="danger-action"')
  })

  it('routes semantic controls through contrast-safe pairs instead of raw ROM roles', () => {
    const rootRule = styles.match(/:root\s*\{([^}]*)\}/)?.[1]
    const focusRule = styles.match(/:where\(button, a\[href\], input, select, textarea\):focus-visible\s*\{([^}]*)\}/)?.[1]
    const trainerSelection = styles.match(/\.trainer-destination-action\[aria-pressed="true"\]\s*\{([^}]*)\}/)?.[1]
    const settingsUpload = styles.match(/\.settings-upload\s*\{([^}]*)\}/)?.[1]
    const setupAction = styles.match(/\.setup-action\s*\{([^}]*)\}/)?.[1]
    const setupPrimary = styles.match(/\.setup-action-primary\s*\{([^}]*)\}/)?.[1]

    expect(rootRule).toMatch(/--semantic-primary-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-secondary-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-selected-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-surface-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-danger-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-status-bg\s*:/)
    expect(rootRule).toMatch(/--semantic-focus\s*:/)
    expect(focusRule).toMatch(/var\(--semantic-focus\)/)
    expect(trainerSelection).toMatch(/color\s*:\s*var\(--semantic-selected-fg\)/)
    expect(trainerSelection).toMatch(/background\s*:\s*var\(--semantic-selected-bg\)/)
    expect(settingsUpload).toMatch(/color\s*:\s*var\(--semantic-secondary-fg\)/)
    expect(settingsUpload).toMatch(/background\s*:\s*var\(--semantic-secondary-bg\)/)
    expect(setupAction).toMatch(/color\s*:\s*var\(--semantic-secondary-fg\)/)
    expect(setupPrimary).toMatch(/color\s*:\s*var\(--semantic-primary-fg\)/)
    expect(styles).toMatch(/\.capability-actions button[^{}]*\{[^}]*color\s*:\s*var\(--semantic-primary-fg\)/)
    expect(styles).toMatch(/\.debug-actions \.danger-action[^{}]*\{[^}]*background\s*:\s*var\(--semantic-danger-bg\)/)
    expect(settingsSource).toContain('class="danger-action"')
    expect(settingsSource).toContain('REMOVE UNUSED GAME DATA')
  })

  it('keeps canonical route headings in the header and avoids a nested Map main landmark', () => {
    expect(componentsSource).toContain('<h1 ref={headingRef} tabIndex={-1}>{title}</h1>')
    expect(pokemonAreaSource).not.toContain('<h1')
    expect(battleSource).not.toContain('<h1')
    expect(readFileSync(join(process.cwd(), 'src', 'pages', 'MapPage.tsx'), 'utf8')).not.toMatch(/<main[\s>]/)
  })

  it('reserves global feedback below the screen instead of overlaying route controls', () => {
    const deviceRule = styles.match(/(?:^|\n)\.device-screen\s*\{([^}]*)\}/)?.[1]
    const hostRule = styles.match(/\.screen-host\s*\{([^}]*)\}/)?.[1]
    const feedbackRule = styles.match(/\.global-feedback\s*\{([^}]*)\}/)?.[1]

    expect(deviceRule).toMatch(/grid-template-rows\s*:\s*minmax\(0, 1fr\) auto/)
    expect(hostRule).toMatch(/position\s*:\s*relative/)
    expect(feedbackRule).toMatch(/pointer-events\s*:\s*none/)
    expect(styles).toMatch(/\.error-toast button\s*\{[^}]*pointer-events\s*:\s*auto/)
  })

  it('enforces the physical text floor on the smallest new-route labels', () => {
    expect(styles).toContain('--ui-min-text: 12px')
    expect(styles).toMatch(/\.setup-screen\s*\{[^}]*--ui-min-text\s*:\s*12\.4px/)
    expect(styles).toMatch(/\.settings-screen\s*\{[^}]*--ui-min-text\s*:\s*12\.4px/)
    expect(styles).toMatch(/\.challenge-card > div:first-child span[^{}]*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.6rem\)/)
    expect(styles).toMatch(/\.damage-forecast-grid small[^{}]*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.64em\)/)
    expect(styles).toMatch(/\.area-guide-exits > small[^{}]*\{[^}]*font\s*:\s*900 max\(var\(--ui-min-text\),\s*\.58rem\)/)
    expect(styles).toMatch(/\.height-comparison-heading strong\s*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.76em\)/)
    expect(styles).toMatch(/\.height-ruler-line i\s*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.54em\)/)
    expect(styles).toMatch(/\.setting-note,\s*\.range-setting span\s*\{[^}]*font-size\s*:\s*max\(var\(--ui-min-text\),\s*\.85em\)/)
  })
})
