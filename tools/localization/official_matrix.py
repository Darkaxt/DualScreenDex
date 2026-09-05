"""Task387: offline evidence validator, NOT a ROM parser or a semantic oracle.

CLI: python -B official_matrix.py --plan ABSOLUTE_JSON --plan-sha256 SHA256
                                 --source-commit FULL_COMMIT
Emits allowlisted JSON to stdout; exit 0 = EVIDENCE_VALIDATED, 1 = BLOCKED.
No files are written. A blocked run never publishes partial positive controls.

Input contract v1 (executable synthetic example: test_official_matrix.Fixture):
* Independently reviewed/pinned plan: schemaVersion=1, sourceCommit, source
  {path,sha256}, reportSchemaVersion, cacheSchemaVersion, parserSchemaVersion,
  requiredSections (exact shared inventory), controls, runs. Paths are absolute
  local regular files/directories; no URI, network, discovery or ROM access.
* controls: 44 explicit {sha256,family,language,release,codecId,codecVersion}.
  KO releases MUST be GOLD/SILVER; all other releases are opaque expectations.
  These pins/codec expectations are validation inputs, NEVER production routing.
* Each run: manifest/report/receipt/evidence/api/oracle {path,sha256}, cacheDir,
  generatorSha256. Runs partition the exact controls (e.g. 35 + 9). Manifest
  is the existing private list; sha256/family/language must match, KO release
  must match explicitly. Other private fields are ignored, never published.
  Report/receipt use CorpusReport/CorpusExecutionReceipt shapes. All versions,
  source/generator/report pins and exact input counts must agree.
* evidence/api/oracle (and referenced proof documents): schemaVersion=1,
  binding={sourceCommit,sourceSha256,reportSha256,receiptSha256,generatorSha256}.
  controls is keyed by exact SHA, with no missing/extra identities.
* evidence.controls[sha]: cacheSha256, checks. Seven mandatory checks below
  each contain tests>0, failures=errors=skipped=0 and structured data. The
  independent oracle supplies expected data, not a free-form 'pass'.
* api.controls[sha]: cacheSha256, bootstrap (actual captured response), plus
  captured field responses in any JSON layout. oracle.controls[sha].bootstrap
  supplies {pointer,value} assertions for romSha256/language/authority/
  parserInvocations (ROM_DEFAULT and zero reparses are mandatory).
* oracle.controls[sha]: checks, bootstrap, capabilities (all 15 names).
  Each capability: disposition ACCEPTED or EXCLUDED, coveredRecords,
  expectedRecords, records (independent applicable field SAMPLES, not a full
  per-record oracle), excluded, proof. Sample={id,cache:{pointer,value},
  api:{pointer,value}}; cache pointers address the reopened language overlay,
  API pointers address this control's captured response object. Partial
  coverage is accepted only with every excluded ID independently accounted.
  EXCLUDED means source-proven not-applicable, NOT merely unavailable/skipped.
  Zero-count exclusions still require an explicit NOT_APPLICABLE proof and
  absence={cache:{pointer,value},api:{pointer,value}}. Each value must be an
  empty object/list or null and must match actual reopened/captured fields.
* proof={artifact:{path,sha256},pointer}; resolves to a structured object in a
  bound JSON document, with kind FIELD_ACCEPTANCE/RESERVED_SLOT/NOT_APPLICABLE,
  romSha256, capability, recordIds, coveredRecords, expectedRecords and
  sourceSlice={offset,length,sha256}. The slice must exist in the independently
  pinned source artifact. Excluded={id,proof}; reserved/excluded IDs cannot
  overlap accepted samples. Missing fields or unknown dispositions BLOCK.

Source artifact and slices are explicit retained evidence inputs, not inferred
hashes, codecs, numeric-order mappings or compiled-ROM truth. This tool verifies
provenance, source references, sample agreement, coverage accounting, report/cache
consistency and mandatory check results. It cannot establish that an external
coverage decision or semantic oracle is true; those require independent review.
Existing reports alone lack normalized checks/API captures/independent coverage
and exclusion references. No adapter synthesizes them from parser status.

Read bounds: 32 MiB JSON/source, 128 MiB aggregate JSON, 128 MiB database,
32 MiB inflated section, 128 MiB inflated catalog, 64 MiB encoded catalog;
SQLite VM deadline 5 seconds per cache. Larger evidence is an explicit blocker.
SQLite uses mode=ro, immutable=1, query_only, trusted_schema=OFF, a read
transaction and integrity_check; WAL/journal sidecars are rejected. All chunks
and encoded digests are checked, not just the two localization sections.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import sqlite3
import sys
import time
import zlib

FAMILIES = frozenset("RED_BLUE YELLOW GOLD_SILVER CRYSTAL RUBY_SAPPHIRE EMERALD FIRERED_LEAFGREEN".split())
CAPABILITIES = frozenset("SPECIES_NAMES SPECIES_DESCRIPTIONS MOVE_NAMES MOVE_DESCRIPTIONS ABILITY_NAMES ABILITY_DESCRIPTIONS TYPE_NAMES NATURE_NAMES ITEM_NAMES AREA_NAMES LOCAL_MAP_NAMES WORLD_REGION_NAMES WORLD_LOCATION_NAMES ENCOUNTER_AREA_NAMES POI_TEXT".split())
SECTIONS = frozenset("language_manifest species moves types abilities natures type_chart encounters capture_balls learnset_rulesets runtime_metadata world_maps trainer_assets local_maps theme capabilities diagnostics".split())
CHECKS = frozenset("rawHeader codecGoldenVectors structuralAuthority reopenParity projectionIsolation typeSemantics apiBootstrap".split())
MAX_JSON_BYTES = 32 * 1024 * 1024
MAX_DATABASE_BYTES = 128 * 1024 * 1024
MAX_AGGREGATE_BYTES = 128 * 1024 * 1024
MAX_RECORDS = 100000


class Blocked(Exception):
    """Only fixed codes, never raw input or exception messages, reach stdout."""


def require(condition, code):
    if not condition:
        raise Blocked(code)


def integer(value, low=0, high=MAX_RECORDS):
    return type(value) is int and low <= value <= high


def digest(value):
    return hashlib.sha256(value).hexdigest()


def hash_value(value, length=64):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{%d}" % length, value) is not None


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "INPUT_SCHEMA")
        result[key] = value
    return result


def parse_json(data):
    try:
        return json.loads(data, object_pairs_hook=unique_pairs,
                          parse_constant=lambda _: require(False, "INPUT_SCHEMA"))
    except (ValueError, UnicodeError, RecursionError):
        raise Blocked("INPUT_SCHEMA") from None


def local_path(value, directory=False):
    require(isinstance(value, str) and 0 < len(value) <= 4096, "INPUT_PATH")
    require(not value.replace("\\", "/").startswith("//"), "INPUT_PATH")
    p = Path(value)
    require(p.is_absolute(), "INPUT_PATH")
    require(p.is_dir() if directory else p.is_file(), "INPUT_MISSING")
    return p


def read_bounded(path, limit):
    require(path.stat().st_size <= limit, "INPUT_LIMIT")
    with path.open("rb") as handle:
        data = handle.read(limit + 1)
    require(len(data) <= limit, "INPUT_LIMIT")
    return data


class Inputs:
    def __init__(self):
        self.documents = {}
        self.bytes_read = 0

    def raw(self, ref, limit=MAX_JSON_BYTES):
        require(isinstance(ref, dict) and hash_value(ref.get("sha256")), "INPUT_SCHEMA")
        data = read_bounded(local_path(ref["path"]), limit)
        require(digest(data) == ref["sha256"], "ARTIFACT_DIGEST")
        self.bytes_read += len(data)
        require(self.bytes_read <= MAX_AGGREGATE_BYTES, "INPUT_LIMIT")
        return data

    def document(self, ref):
        key = (ref["path"], ref["sha256"])
        if key not in self.documents:
            self.documents[key] = parse_json(self.raw(ref))
        return self.documents[key]


def pointer(value, path):
    require(isinstance(path, str) and path.startswith("/") and len(path) <= 2048, "EVIDENCE_REFERENCE")
    parts = path[1:].split("/")
    require(len(parts) <= 32, "EVIDENCE_REFERENCE")
    try:
        for part in parts:
            require(re.search(r"~(?![01])", part) is None, "EVIDENCE_REFERENCE")
            key = part.replace("~1", "/").replace("~0", "~")
            if isinstance(value, list):
                require(re.fullmatch(r"0|[1-9][0-9]{0,6}", key) is not None, "EVIDENCE_REFERENCE")
                value = value[int(key)]
            else:
                require(isinstance(value, dict), "EVIDENCE_REFERENCE")
                value = value[key]
        return value
    except (KeyError, IndexError):
        raise Blocked("EVIDENCE_REFERENCE") from None


def assertion(value, check, code):
    require(isinstance(check, dict) and "value" in check, code)
    require(canonical(pointer(value, check["pointer"])) == canonical(check["value"]), code)


def identities(rows, key):
    require(isinstance(rows, list) and len(rows) <= 44, "INPUT_COUNT")
    result = {}
    for row in rows:
        identity = key(row)
        require(hash_value(identity) and identity not in result, "CONTROL_IDENTITY")
        result[identity] = row
    return result


def matrix_controls(plan):
    rows = plan["controls"]
    require(isinstance(rows, list) and len(rows) == 44, "MATRIX_COVERAGE")
    controls = identities(rows, lambda c: c["sha256"])
    cells = Counter((c["family"], c["language"]) for c in rows)
    expected = Counter({(f, lang): 1 for f in FAMILIES for lang in ("en", "fr", "de", "it", "es", "ja")})
    expected[("GOLD_SILVER", "ko")] = 2
    require(cells == expected, "MATRIX_COVERAGE")
    require({c["release"] for c in rows if c["language"] == "ko"} == {"GOLD", "SILVER"}, "MATRIX_COVERAGE")
    for c in rows:
        require(isinstance(c["codecId"], str) and 0 < len(c["codecId"]) <= 128 and
                integer(c["codecVersion"], 1), "INPUT_SCHEMA")
    return controls


def bound_document(document, binding):
    require(isinstance(document, dict) and type(document.get("schemaVersion")) is int and document["schemaVersion"] == 1 and
            document.get("binding") == binding, "EVIDENCE_BINDING")


def language_manifest(manifest, control):
    require(isinstance(manifest, dict) and manifest.get("status") == "RESOLVED" and
            manifest.get("defaultLanguage") == control["language"], "LANGUAGE_AUTHORITY")
    projections = manifest.get("projections")
    require(isinstance(projections, list) and len(projections) == 1, "LANGUAGE_AUTHORITY")
    p = projections[0]
    require(p.get("status") == "RESOLVED" and p.get("language") == control["language"] and
            p.get("codecId") == control["codecId"] and type(p.get("codecVersion")) is int and
            p["codecVersion"] == control["codecVersion"], "LANGUAGE_AUTHORITY")
    require(isinstance(p.get("evidence"), list) and bool(p["evidence"]), "LANGUAGE_AUTHORITY")


def cache_digest(path):
    require(path.stat().st_size <= MAX_DATABASE_BYTES, "INPUT_LIMIT")
    h = hashlib.sha256()
    count = 0
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            count += len(chunk)
            require(count <= MAX_DATABASE_BYTES, "INPUT_LIMIT")
            h.update(chunk)
    return h.hexdigest()


def read_cache(path, expected_digest, control, plan):
    require(hash_value(expected_digest) and cache_digest(path) == expected_digest, "CACHE_DIGEST")
    require(not any(Path(str(path) + suffix).exists() for suffix in ("-wal", "-shm", "-journal")), "CACHE_SIDECAR")
    deadline = time.monotonic() + 5
    db = sqlite3.connect(path.as_uri() + "?mode=ro&immutable=1", uri=True, timeout=1)
    retained = {}
    try:
        db.setlimit(sqlite3.SQLITE_LIMIT_LENGTH, MAX_JSON_BYTES + 1)
        db.set_progress_handler(lambda: int(time.monotonic() > deadline), 1000)
        db.execute("PRAGMA query_only=ON")
        db.execute("PRAGMA trusted_schema=OFF")
        db.execute("BEGIN")
        require(db.execute("PRAGMA query_only").fetchone() == (1,), "CACHE_INVALID")
        require(db.execute("PRAGMA integrity_check(1)").fetchall() == [("ok",)], "CACHE_INVALID")
        tables = db.execute("SELECT name,type FROM sqlite_schema WHERE name IN ('catalog_metadata','catalog_sections','catalog_section_chunks')").fetchall()
        require(len(tables) == 3 and all(kind == "table" for _, kind in tables), "CACHE_SCHEMA")
        metadata = db.execute("SELECT id,schema_version,parser_schema_version,sha256,family,is_complete FROM catalog_metadata LIMIT 2").fetchall()
        require(metadata == [(1, plan["cacheSchemaVersion"], plan["parserSchemaVersion"],
                              control["sha256"], control["family"], 1)], "CACHE_SCHEMA")
        sections = db.execute("SELECT name,encoding,length(payload) FROM catalog_sections LIMIT 34").fetchall()
        expected = SECTIONS | {"language_overlay:" + control["language"]}
        require(len(sections) == len(expected) and {r[0] for r in sections} == expected, "CACHE_SECTIONS")
        groups = db.execute("SELECT section_name,count(*),sum(length(payload)),max(length(payload)) FROM catalog_section_chunks GROUP BY section_name LIMIT 34").fetchall()
        require(len(groups) == len(expected) and {r[0] for r in groups} == expected, "CACHE_CHUNKS")
        encoded_total = inflated_total = 0
        for name, count, size, maximum in groups:
            overlay = name.startswith("language_overlay:")
            require(integer(count, 1, 32 if overlay else 128) and
                    integer(size, 1, 8 * 1024 * 1024 if overlay else MAX_JSON_BYTES) and
                    integer(maximum, 1, 256 * 1024), "CACHE_CHUNKS")
            encoded_total += size
        require(encoded_total <= 64 * 1024 * 1024, "INPUT_LIMIT")
        for name, encoding, length in sections:
            require(encoding == "gzip+json+chunks-v1" and length == 32, "CACHE_SCHEMA")
            chunks = db.execute("SELECT chunk_index,payload FROM catalog_section_chunks WHERE section_name=? ORDER BY chunk_index LIMIT 129", (name,)).fetchall()
            require([r[0] for r in chunks] == list(range(len(chunks))), "CACHE_CHUNKS")
            payload = b"".join(r[1] for r in chunks)
            saved_digest = db.execute("SELECT payload FROM catalog_sections WHERE name=?", (name,)).fetchone()[0]
            require(hashlib.sha256(payload).digest() == saved_digest, "CACHE_SECTION_DIGEST")
            with gzip.GzipFile(fileobj=io.BytesIO(payload)) as stream:
                raw = stream.read(MAX_JSON_BYTES + 1)
            inflated_total += len(raw)
            require(len(raw) <= MAX_JSON_BYTES and inflated_total <= MAX_AGGREGATE_BYTES, "INPUT_LIMIT")
            decoded = parse_json(raw)
            if name == "language_manifest" or name.startswith("language_overlay:"):
                retained[name] = decoded
        language_manifest(retained["language_manifest"], control)
        overlay = retained["language_overlay:" + control["language"]]
        require(overlay.get("language") == control["language"] and integer(overlay.get("overlayVersion"), 1), "LANGUAGE_AUTHORITY")
    except sqlite3.Error:
        raise Blocked("CACHE_INVALID") from None
    except (gzip.BadGzipFile, EOFError, zlib.error):
        raise Blocked("CACHE_INVALID") from None
    finally:
        db.close()
    require(cache_digest(path) == expected_digest, "CACHE_DIGEST")
    return retained


def required_checks(observed, expected, api):
    require(isinstance(observed, dict) and set(observed) == CHECKS and
            isinstance(expected, dict) and set(expected) == CHECKS, "REQUIRED_CHECK")
    for name in CHECKS:
        check = observed[name]
        require(isinstance(check, dict) and check.get("status", "PASS") == "PASS" and integer(check.get("tests"), 1) and
                all(type(check.get(k)) is int and check[k] == 0 for k in ("failures", "errors", "skipped")), "REQUIRED_CHECK")
        data = check.get("data")
        require(isinstance(data, dict) and data and canonical(data) == canonical(expected[name]), "REQUIRED_CHECK")
        if name == "rawHeader":
            valid = hash_value(data.get("rawHeaderSha256")) and integer(data.get("byteCount"), 1, 512)
        elif name == "codecGoldenVectors":
            valid = hash_value(data.get("vectorSetSha256")) and integer(data.get("vectorCount"), 1) and type(data.get("matchedCount")) is int and data["matchedCount"] == data["vectorCount"]
        elif name == "structuralAuthority":
            valid = hash_value(data.get("consumerEvidenceSha256")) and integer(data.get("corroboratingTableCount"), 2)
        elif name == "reopenParity":
            valid = hash_value(data.get("beforeCatalogSha256")) and data.get("beforeCatalogSha256") == data.get("afterCatalogSha256")
        elif name == "projectionIsolation":
            valid = integer(data.get("fieldsChecked"), 15) and all(type(data.get(k)) is int and data[k] == 0 for k in ("mixedFields", "fallbackFields", "sharedTextFields"))
        elif name == "typeSemantics":
            valid = integer(data.get("typesChecked"), 1) and all(type(data.get(k)) is int and data[k] == 0 for k in ("unresolvedTypes", "mismatchedTypes"))
        else:
            valid = data.get("responseSha256") == digest(canonical(api["bootstrap"])) and type(data.get("parserInvocations")) is int and data["parserInvocations"] == 0
        require(valid, "REQUIRED_CHECK")


def referenced_proof(ref, inputs, binding, source, identity, cap, kinds):
    require(isinstance(ref, dict), "EVIDENCE_REFERENCE")
    document = inputs.document(ref["artifact"])
    bound_document(document, binding)
    proof = pointer(document, ref["pointer"])
    require(isinstance(proof, dict) and proof.get("kind") in kinds and
            proof.get("romSha256") == identity and proof.get("capability") == cap, "EVIDENCE_REFERENCE")
    location = proof.get("sourceSlice")
    require(isinstance(location, dict) and integer(location.get("offset"), 0, len(source)) and
            integer(location.get("length"), 1, min(len(source), 65536)), "EVIDENCE_REFERENCE")
    start, length = location["offset"], location["length"]
    require(start + length <= len(source) and digest(source[start:start + length]) == location.get("sha256"), "EVIDENCE_REFERENCE")
    ids = proof.get("recordIds")
    require(isinstance(ids, list) and len(ids) <= MAX_RECORDS and
            all(isinstance(i, str) and 0 < len(i) <= 128 for i in ids) and len(set(ids)) == len(ids), "EVIDENCE_REFERENCE")
    return proof


def capability_audit(report, overlay, api, oracle, inputs, binding, source, identity):
    states = overlay["localizedCapabilities"]
    require(isinstance(states, list) and len(states) == 15 and
            {s["capability"] for s in states} == CAPABILITIES and
            isinstance(report, dict) and set(report) == CAPABILITIES and
            isinstance(oracle, dict) and set(oracle) == CAPABILITIES, "CAPABILITY_INVENTORY")
    cached = {s["capability"]: s for s in states}
    result = {}
    for cap in sorted(CAPABILITIES):
        observed, persisted, accept = report[cap], cached[cap], oracle[cap]
        covered, total = observed.get("coveredRecords"), observed.get("expectedRecords")
        require(integer(covered) and integer(total) and covered <= total, "CAPABILITY_COUNTS")
        for state in (persisted, accept):
            require(type(state.get("coveredRecords")) is int and type(state.get("expectedRecords")) is int and
                    (state["coveredRecords"], state["expectedRecords"]) == (covered, total), "CAPABILITY_COUNTS")
        status = observed.get("status")
        require(status in {"AVAILABLE", "PARTIAL", "NOT_FOUND", "NOT_APPLICABLE"} and persisted.get("status") == status, "CAPABILITY_DISPOSITION")
        disposition = accept.get("disposition")
        require(disposition in {"ACCEPTED", "EXCLUDED"}, "CAPABILITY_DISPOSITION")
        samples, excluded = accept["records"], accept["excluded"]
        require(isinstance(samples, list) and isinstance(excluded, list) and len(excluded) == total - covered and
                len(samples) <= covered, "CAPABILITY_COUNTS")
        sample_ids = [s["id"] for s in samples]
        excluded_ids = [e["id"] for e in excluded]
        ids = sample_ids + excluded_ids
        require(all(isinstance(i, str) and 0 < len(i) <= 128 for i in ids) and len(set(ids)) == len(ids), "CAPABILITY_COUNTS")
        if disposition == "ACCEPTED":
            require(covered > 0 and samples and status in {"AVAILABLE", "PARTIAL"}, "CAPABILITY_DISPOSITION")
            kinds = {"FIELD_ACCEPTANCE"}
        else:
            require(covered == 0 and not samples and cap not in {"SPECIES_NAMES", "MOVE_NAMES", "TYPE_NAMES"} and
                    status in {"NOT_FOUND", "NOT_APPLICABLE"}, "CAPABILITY_DISPOSITION")
            absence = accept.get("absence")
            require(isinstance(absence, dict) and set(absence) == {"cache", "api"}, "FIELD_ACCEPTANCE")
            for target, data in (("cache", overlay), ("api", api)):
                require("value" in absence[target] and absence[target]["value"] in (None, {}, []), "FIELD_ACCEPTANCE")
                assertion(data, absence[target], "FIELD_ACCEPTANCE")
            kinds = {"NOT_APPLICABLE"}
        proof = referenced_proof(accept["proof"], inputs, binding, source, identity, cap, kinds)
        require(type(proof.get("coveredRecords")) is int and type(proof.get("expectedRecords")) is int and
                (proof["coveredRecords"], proof["expectedRecords"]) == (covered, total) and
                set(sample_ids).issubset(proof["recordIds"]), "CAPABILITY_COUNTS")
        for sample in samples:
            require(sample["cache"].get("value") not in (None, "", "pass", "PASS") and
                    canonical(sample["cache"]["value"]) == canonical(sample["api"].get("value")), "FIELD_ACCEPTANCE")
            assertion(overlay, sample["cache"], "FIELD_ACCEPTANCE")
            assertion(api, sample["api"], "FIELD_ACCEPTANCE")
        for exclusion in excluded:
            proof = referenced_proof(exclusion["proof"], inputs, binding, source, identity, cap,
                                     {"RESERVED_SLOT", "NOT_APPLICABLE"})
            require(exclusion["id"] in proof["recordIds"], "EVIDENCE_REFERENCE")
        result[cap] = {"disposition": disposition, "coveredRecords": covered,
                       "expectedRecords": total, "excludedRecords": len(excluded), "validatedSamples": len(samples)}
    return result


def validate_run(run, plan, controls, inputs, source):
    manifest = identities(inputs.document(run["manifest"]), lambda c: c["sha256"])
    require(manifest and set(manifest).issubset(controls), "CONTROL_IDENTITY")
    for identity, c in manifest.items():
        expected = controls[identity]
        require((c["family"], c["language"]) == (expected["family"], expected["language"]) and
                (c["language"] != "ko" or c.get("release") == expected["release"]), "CONTROL_IDENTITY")
    report, receipt = inputs.document(run["report"]), inputs.document(run["receipt"])
    require(type(report.get("schemaVersion")) is int and type(receipt.get("schemaVersion")) is int and
            report["schemaVersion"] == plan["reportSchemaVersion"] and receipt["schemaVersion"] == 1,
            "REPORT_SCHEMA")
    generator = run["generatorSha256"]
    require(hash_value(generator) and report.get("execution") == {"sourceCommit": plan["sourceCommit"], "generatorSha256": generator} and
            receipt.get("sourceCommit") == plan["sourceCommit"] and
            receipt.get("generator") == {"name": "parser-cli", "schemaVersion": plan["reportSchemaVersion"], "sha256": generator} and
            receipt.get("rawReportSha256") == run["report"]["sha256"], "PROVENANCE")
    rows = identities(report["results"], lambda r: r["result"]["sha256"])
    require(type(receipt.get("inputCount")) is int and receipt["inputCount"] == len(manifest) == len(rows), "INPUT_COUNT")
    require(set(rows) == set(manifest), "CONTROL_IDENTITY")
    binding = {"sourceCommit": plan["sourceCommit"], "sourceSha256": plan["source"]["sha256"],
               "reportSha256": run["report"]["sha256"], "receiptSha256": run["receipt"]["sha256"],
               "generatorSha256": generator}
    evidence, api, oracle = (inputs.document(run[k]) for k in ("evidence", "api", "oracle"))
    require(len({run[k]["sha256"] for k in ("report", "evidence", "api", "oracle")}) == 4, "EVIDENCE_BINDING")
    for doc in (evidence, api, oracle):
        bound_document(doc, binding)
        require(isinstance(doc.get("controls"), dict) and set(doc["controls"]) == set(rows), "CONTROL_IDENTITY")
    cache_dir = local_path(run["cacheDir"], directory=True)
    public = []
    for identity, row in rows.items():
        c, observed, capture, expected = controls[identity], evidence["controls"][identity], api["controls"][identity], oracle["controls"][identity]
        require(all(row.get(k) is None for k in ("error", "catalogError", "persistenceError")) and
                row.get("samples", {}).get("referenceErrors") == [], "REPORT_ERROR")
        result = row["result"]
        require(result.get("status") == "SELECTED" and result.get("selectedFamily") == c["family"], "LANGUAGE_AUTHORITY")
        probes = [p for p in result["probes"] if p.get("family") == c["family"]]
        require(len(probes) == 1, "LANGUAGE_AUTHORITY")
        manifest_value = probes[0]["resolvedLayout"]["languageManifest"]
        language_manifest(manifest_value, c)
        require(row.get("persistence", {}).get("fileName") == identity + ".sqlite", "CACHE_IDENTITY")
        path = local_path(str(cache_dir / (identity + ".sqlite")))
        cached = read_cache(path, observed["cacheSha256"], c, plan)
        require(capture.get("cacheSha256") == observed["cacheSha256"], "EVIDENCE_BINDING")
        # Runtime-selection layout is not persisted in StoredLanguageManifest.
        for field in ("status", "defaultLanguage", "projections"):
            require(canonical(cached["language_manifest"].get(field)) == canonical(manifest_value.get(field)), "LANGUAGE_AUTHORITY")
        required_checks(observed["checks"], expected["checks"], capture)
        bootstrap = expected["bootstrap"]
        fixed = {"romSha256": identity, "language": c["language"], "authority": "ROM_DEFAULT", "parserInvocations": 0}
        require(set(bootstrap) == set(fixed), "REQUIRED_CHECK")
        for key, value in fixed.items():
            require(canonical(bootstrap[key].get("value")) == canonical(value), "REQUIRED_CHECK")
            assertion(capture["bootstrap"], bootstrap[key], "API_ACCEPTANCE")
        caps = capability_audit(row["catalog"]["localizedCapabilities"], cached["language_overlay:" + c["language"]],
                                capture, expected["capabilities"], inputs, binding, source, identity)
        public.append({"romSha256": identity, "family": c["family"], "language": c["language"],
                       "status": "EVIDENCE_VALIDATED", "capabilities": caps})
    return public


def empty_result():
    return {"schemaVersion": 1, "status": "BLOCKED", "expectedCells": 43,
            "expectedControls": 44, "validatedCells": 0, "validatedControls": 0,
            "controls": [], "blockers": []}


def validate(plan_path, plan_sha256, source_commit):
    """Fail closed with sanitized fixed blocker codes; never expose exception text."""
    output = empty_result()
    try:
        require(hash_value(source_commit, 40), "PROVENANCE")
        inputs = Inputs()
        plan = inputs.document({"path": str(plan_path), "sha256": plan_sha256})
        require(type(plan.get("schemaVersion")) is int and plan["schemaVersion"] == 1 and all(integer(plan.get(k), 1) for k in
                ("reportSchemaVersion", "cacheSchemaVersion", "parserSchemaVersion")), "INPUT_SCHEMA")
        require(plan.get("sourceCommit") == source_commit, "PROVENANCE")
        require(isinstance(plan["requiredSections"], list) and len(plan["requiredSections"]) == len(SECTIONS) and
                set(plan["requiredSections"]) == SECTIONS, "CACHE_SCHEMA")
        controls = matrix_controls(plan)
        source = inputs.raw(plan["source"])
        require(isinstance(plan["runs"], list) and 1 <= len(plan["runs"]) <= 44, "INPUT_COUNT")
        public = []
        for run in plan["runs"]:
            public.extend(validate_run(run, plan, controls, inputs, source))
        require(len(public) == 44 and {c["romSha256"] for c in public} == set(controls), "CONTROL_IDENTITY")
        output.update(status="EVIDENCE_VALIDATED", validatedCells=43, validatedControls=44,
                      sourceCommit=source_commit, planSha256=plan_sha256,
                      controls=sorted(public, key=lambda c: (c["family"], c["language"], c["romSha256"])))
    except Blocked as error:
        output["blockers"] = [{"code": error.args[0]}]
    except (OSError, sqlite3.Error):
        output["blockers"] = [{"code": "INPUT_IO"}]
    except (KeyError, TypeError, ValueError, AttributeError, IndexError, RecursionError, OverflowError):
        output["blockers"] = [{"code": "INPUT_SCHEMA"}]
    return output


class Arguments(argparse.ArgumentParser):
    def error(self, message):
        raise Blocked("CLI_ARGUMENTS")


def main(argv=None):
    parser = Arguments(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter, allow_abbrev=False)
    parser.add_argument("--plan", required=True)
    parser.add_argument("--plan-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    try:
        args = parser.parse_args(argv)
        result = validate(Path(args.plan), args.plan_sha256, args.source_commit)
    except Blocked:
        result = empty_result()
        result["blockers"] = [{"code": "CLI_ARGUMENTS"}]
    print(json.dumps(result, sort_keys=True, ensure_ascii=True))
    return 0 if result["status"] == "EVIDENCE_VALIDATED" else 1


if __name__ == "__main__":
    sys.exit(main())
