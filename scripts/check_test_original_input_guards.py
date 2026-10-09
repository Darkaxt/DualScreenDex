"""Check the audited host-test original reader surface, not arbitrary filesystem I/O."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('parser-core', 'catalog-store', 'parser-cli')
# These read generated/source/runtime artifacts, or are explicitly invoked non-JUnit matrices.
NON_ORIGINAL_READERS = {
    'parser-core': {'CodecGoldenEvidence.kt'},
    'catalog-store': set(),
    'parser-cli': {
        'CatalogPersistenceObservationCliTest.kt', 'RawHeaderObservationCliTest.kt',
        'CorpusScannerTest.kt', 'ExecutionReceiptTest.kt',
        'EvolutionFirst50Matrix.kt', 'GbGbcLocalMapMatrix.kt', 'MapFirst50Matrix.kt',
    },
}
RAW_READ = re.compile(r'Files\.(?:readAllBytes|newInputStream)\(|\bfile\.readBytes\(')


def violations(module, name, source):
    if name in NON_ORIGINAL_READERS[module] or name == 'OriginalRomTestAccess.kt':
        return []
    result = []
    for number, line in enumerate(source.splitlines(), 1):
        if not RAW_READ.search(line):
            continue
        # Keep the existing bounded stream decoder inside the guarded callback.
        if 'OriginalRomTestAccess.read { Files.newInputStream(path).use(RomImage::from) }' in line:
            continue
        # This second read is a public source tilemap, after the guarded native input.
        if name == 'GbaSmolDecoderRealControlTest.kt' and line.strip() == 'val source = Files.readAllBytes(':
            continue
        result.append(number)
    if name == 'RomSourceLoaderRealArchiveTest.kt':
        if 'OriginalRomTestAccess.read {\n            listOf(RomSourceLoader.load(raw)' not in source:
            result.append(0)
    return result


def selftest():
    assert violations('parser-core', 'NewLiveRomTest.kt', 'Files.readAllBytes(path)') == [1]
    assert violations('parser-core', 'NewLiveRomTest.kt', 'Files.newInputStream(path)') == [1]
    assert violations('parser-core', 'MixedTest.kt', 'RomImage(file.readBytes())') == [1]
    assert not violations('parser-core', 'MixedTest.kt', 'OriginalRomTestAccess.readAllBytes(path)')
    assert not violations('parser-core', 'NatureTest.kt',
                          'OriginalRomTestAccess.read { Files.newInputStream(path).use(RomImage::from) }')
    assert violations('parser-core', 'RomSourceLoaderRealArchiveTest.kt', 'RomSourceLoader.load(raw)') == [0]
    print('6 generated reader-audit controls passed; no original input opened')


def main():
    errors = []
    helpers = []
    files = calls = 0
    for module in MODULES:
        paths = sorted((ROOT / module / 'src/test/kotlin').rglob('*.kt'))
        module_helpers = [p for p in paths if p.name == 'OriginalRomTestAccess.kt']
        assert len(module_helpers) == 1, module
        body = module_helpers[0].read_text(encoding='utf-8').split('\n', 1)[1]
        helpers.append(body)
        for path in paths:
            source = path.read_text(encoding='utf-8')
            if path.name in {'EvolutionFirst50Matrix.kt', 'GbGbcLocalMapMatrix.kt', 'MapFirst50Matrix.kt'}:
                assert '@JvmStatic' in source and 'fun main(' in source and '@Test' not in source, path
            files += 1
            calls += source.count('OriginalRomTestAccess.read')
            for line in violations(module, path.name, source):
                errors.append(f'{path.relative_to(ROOT)}:{line}: unguarded audited reader')
    assert len(set(helpers)) == 1, 'module-local guards differ'
    assert 'optIn == "true"' in helpers[0], 'missing exact default-deny condition'
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'Audited reader contract passed: {files} Kotlin test sources, {calls} guarded calls')
    print('This is a source/read-boundary check, not complete host filesystem interception.')


if __name__ == '__main__':
    selftest() if sys.argv[1:] == ['--selftest'] else main()
