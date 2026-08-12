import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const styles = readFileSync(join(process.cwd(), 'src', 'styles.css'), 'utf8')

describe('screen layout containment', () => {
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
})
