import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const styles = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8')
const areaGuideSource = readFileSync(join(process.cwd(), 'src', 'pages', 'AreaGuideDrawer.tsx'), 'utf8')
const battleSource = readFileSync(join(process.cwd(), 'src', 'pages', 'BattlePage.tsx'), 'utf8')

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
})
