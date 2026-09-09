"""G6: deterministic private plan assembly, not acceptance or a capture exporter.

CLI (absolute local paths; independently reviewed lowercase SHA-256 pins):
  python -B build_official_matrix_plan.py --request REQUEST.json
      --request-sha256 SHA256 --source-commit FULL_COMMIT --output NEW_PLAN.json

Request v1: {schemaVersion:1, expectations:{path,sha256}, runs:[RUN,...]}.
The independently supplied expectations JSON contains exactly schemaVersion=1,
sourceCommit, source={path,sha256}, reportSchemaVersion, cacheSchemaVersion,
parserSchemaVersion, requiredSections and controls. Controls are the explicit
44 {sha256,family,language,release,codecId,codecVersion} expectations, including
separate Korean GOLD/SILVER. No defaults are inferred from captured observations.

RUN is the existing official_matrix.py v1 run shape: pinned manifest, report,
receipt, evidence, api, oracle JSON references, cacheDir and generatorSha256.
Runs must partition all44. Normalized evidence/API/oracle and referenced proof
JSON must ALREADY carry the exact source/report/receipt/generator binding.
G3 uses the shared validator contract: report16 CLI logical digests match the
restored catalog digest, and nested capture provenance binds the actual cache-only
BootstrapView envelope. It does not repair captures or derive oracle expectations.
Proof sourceSlice bytes address the separately pinned source evidence bundle.
Source is reviewed evidence, never a ROM/cache. Known ROM/cache/dump suffixes
are refused; JSON documents must use .json. Original paths inside manifests
are inert metadata and are never traversed. No paths are discovered.

Missing G0 source-bound runs, G1 checks, G2 vectors, G3 captures or G4 independent
oracle/proofs block assembly. This module never creates expected values, fills
checks, changes bindings, copies field payloads into the plan, or imports test
fixtures. Separate input files/pins do not establish semantic independence;
that still requires external review. Unknown request/expectation/run fields,
missing pins, duplicate identities/JSON keys and contradictory metadata block.

Output is canonical official_matrix.py v1 plan JSON plus newline, with only an
assemblyInputs provenance extension. Existing output is never overwritten.
Exit 0 / PLAN_ASSEMBLED means metadata preflight only, not acceptance. Stdout
contains fixed codes/counts and the plan digest, never private paths or payloads.
Exit 1 / BLOCKED writes no plan. No cache file is opened, hashed or statted;
only the supplied cache directory is checked. Cache bytes, schema, sections,
sidecars, reopened overlay and full matrix acceptance remain for the unchanged
validator using --plan/--plan-sha256/--source-commit. This adapter never calls it.
"""
import argparse
from contextlib import contextmanager
from pathlib import Path
import re
import sys

import official_matrix as matrix

EXPECTATION_KEYS = frozenset("schemaVersion sourceCommit source reportSchemaVersion cacheSchemaVersion parserSchemaVersion requiredSections controls".split())
CONTROL_KEYS = frozenset("sha256 family language release codecId codecVersion".split())
RUN_ROLES = {
    "manifest": "G0_SOURCE_BOUND_RUN", "report": "G0_SOURCE_BOUND_RUN", "receipt": "G0_SOURCE_BOUND_RUN",
    "evidence": "G1_NORMALIZED_CHECKS", "api": "G3_API_CAPTURE", "oracle": "G4_INDEPENDENT_ORACLE",
}
DEFERRED = ("CACHE_PAYLOAD_VALIDATION", "CACHE_SCHEMA_AND_SECTIONS", "REOPENED_CACHE_FIELD_ASSERTIONS", "FINAL_MATRIX_ACCEPTANCE")
PAYLOAD_SUFFIXES = frozenset(".gb .gbc .gba .nds .rom .sqlite .sqlite3 .db .dmp .dump .sav .state .zip .7z".split())


class Refusal(Exception):
    def __init__(self, code, gap):
        self.code, self.gap = code, gap


@contextmanager
def gap(name):
    """Translate only known input/contract failures, without exposing input text."""
    try:
        yield
    except matrix.Blocked as error:
        raise Refusal(error.args[0], name) from None
    except OSError:
        raise Refusal("INPUT_IO", name) from None
    except (KeyError, TypeError, ValueError, AttributeError, IndexError, RecursionError, OverflowError):
        raise Refusal("INPUT_SCHEMA", name) from None


def exact_keys(value, keys):
    matrix.require(isinstance(value, dict) and set(value) == set(keys), "INPUT_SCHEMA")


def absolute_path(value):
    matrix.require(isinstance(value, str) and 0 < len(value) <= 4096 and
                   not value.replace("\\", "/").startswith("//"), "INPUT_PATH")
    path = Path(value)
    matrix.require(path.is_absolute(), "INPUT_PATH")
    return path


class MetadataInputs(matrix.Inputs):
    """Reuse the validator's bounded, duplicate-key rejecting pinned reader."""
    def __init__(self):
        super().__init__()
        self.path_pins = {}
        self.captured_paths = set()
        self.captured_pins = set()

    def reference(self, ref, document=False):
        exact_keys(ref, ("path", "sha256"))
        matrix.require(matrix.hash_value(ref["sha256"]), "INPUT_SCHEMA")
        path = absolute_path(ref["path"])
        matrix.require(path.suffix.lower() not in PAYLOAD_SUFFIXES and
                       not re.search(r"\.(?:sqlite3?|db)-(?:wal|shm|journal)$", path.name, re.I), "INPUT_PATH")
        matrix.require(not document or path.suffix.lower() == ".json", "INPUT_PATH")
        key = str(path.resolve())
        previous = self.path_pins.setdefault(key, ref["sha256"])
        matrix.require(previous == ref["sha256"], "CONFLICTING_PIN")
        return key

    def raw(self, ref, limit=matrix.MAX_JSON_BYTES):
        self.reference(ref)
        return super().raw(ref, limit)

    def document(self, ref):
        self.reference(ref, document=True)
        return super().document(ref)

    def independent(self, ref):
        key = self.reference(ref)
        matrix.require(key not in self.captured_paths and ref["sha256"] not in self.captured_pins, "INDEPENDENT_INPUT_ALIAS")


def pointer_shape(check):
    """Check an expected cache pointer without constructing a pretend overlay."""
    matrix.require(isinstance(check, dict) and "value" in check, "FIELD_ACCEPTANCE")
    value = check.get("pointer")
    matrix.require(isinstance(value, str) and value.startswith("/") and len(value) <= 2048 and
                   len(value[1:].split("/")) <= 32 and re.search(r"~(?![01])", value) is None, "EVIDENCE_REFERENCE")


def capability_metadata(report, api, oracle, inputs, binding, source, identity):
    """The cache-independent subset of capability_audit; never fabricates cache data."""
    matrix.require(isinstance(report, dict) and set(report) == matrix.CAPABILITIES and
                   isinstance(oracle, dict) and set(oracle) == matrix.CAPABILITIES, "CAPABILITY_INVENTORY")

    def proof(ref, cap, kinds):
        inputs.independent(ref["artifact"])
        return matrix.referenced_proof(ref, inputs, binding, source, identity, cap, kinds)

    for cap in sorted(matrix.CAPABILITIES):
        observed, expected = report[cap], oracle[cap]
        covered, total = observed.get("coveredRecords"), observed.get("expectedRecords")
        matrix.require(matrix.integer(covered) and matrix.integer(total) and covered <= total and
                       type(expected.get("coveredRecords")) is int and type(expected.get("expectedRecords")) is int and
                       (expected["coveredRecords"], expected["expectedRecords"]) == (covered, total), "CAPABILITY_COUNTS")
        status, disposition = observed.get("status"), expected.get("disposition")
        matrix.require(status in {"AVAILABLE", "PARTIAL", "NOT_FOUND", "NOT_APPLICABLE"} and
                       disposition in {"ACCEPTED", "EXCLUDED"}, "CAPABILITY_DISPOSITION")
        samples, excluded = expected["records"], expected["excluded"]
        matrix.require(isinstance(samples, list) and isinstance(excluded, list) and
                       len(samples) <= covered and len(excluded) == total - covered, "CAPABILITY_COUNTS")
        sample_ids, excluded_ids = [s["id"] for s in samples], [e["id"] for e in excluded]
        ids = sample_ids + excluded_ids
        matrix.require(all(isinstance(i, str) and 0 < len(i) <= 128 for i in ids) and len(set(ids)) == len(ids), "CAPABILITY_COUNTS")
        if disposition == "ACCEPTED":
            matrix.require(covered > 0 and samples and status in {"AVAILABLE", "PARTIAL"}, "CAPABILITY_DISPOSITION")
            kinds = {"FIELD_ACCEPTANCE"}
        else:
            matrix.require(covered == 0 and not samples and cap not in {"SPECIES_NAMES", "MOVE_NAMES", "TYPE_NAMES"} and
                           status in {"NOT_FOUND", "NOT_APPLICABLE"}, "CAPABILITY_DISPOSITION")
            absence = expected.get("absence")
            matrix.require(isinstance(absence, dict) and set(absence) == {"cache", "api"}, "FIELD_ACCEPTANCE")
            for target in ("cache", "api"):
                pointer_shape(absence[target])
                matrix.require(absence[target]["value"] in (None, {}, []), "FIELD_ACCEPTANCE")
            matrix.assertion(api, absence["api"], "FIELD_ACCEPTANCE")
            kinds = {"NOT_APPLICABLE"}
        authority = proof(expected["proof"], cap, kinds)
        matrix.require(type(authority.get("coveredRecords")) is int and type(authority.get("expectedRecords")) is int and
                       (authority["coveredRecords"], authority["expectedRecords"]) == (covered, total) and
                       set(sample_ids).issubset(authority["recordIds"]), "CAPABILITY_COUNTS")
        for sample in samples:
            pointer_shape(sample["cache"])
            pointer_shape(sample["api"])
            matrix.require(sample["cache"]["value"] not in (None, "", "pass", "PASS") and
                           matrix.canonical(sample["cache"]["value"]) == matrix.canonical(sample["api"]["value"]), "FIELD_ACCEPTANCE")
            matrix.assertion(api, sample["api"], "FIELD_ACCEPTANCE")
        for exclusion in excluded:
            authority = proof(exclusion["proof"], cap, {"RESERVED_SLOT", "NOT_APPLICABLE"})
            matrix.require(exclusion["id"] in authority["recordIds"], "EVIDENCE_REFERENCE")


def run_metadata(run, plan, controls, inputs, source):
    with gap("G0_SOURCE_BOUND_RUN"):
        manifest = matrix.identities(inputs.document(run["manifest"]), lambda c: c["sha256"])
        matrix.require(manifest and set(manifest).issubset(controls), "CONTROL_IDENTITY")
        for identity, c in manifest.items():
            expected = controls[identity]
            matrix.require((c["family"], c["language"]) == (expected["family"], expected["language"]) and
                           (c["language"] != "ko" or c.get("release") == expected["release"]), "CONTROL_IDENTITY")
        report, receipt = inputs.document(run["report"]), inputs.document(run["receipt"])
        matrix.require(type(report.get("schemaVersion")) is int and type(receipt.get("schemaVersion")) is int and
                       report["schemaVersion"] == plan["reportSchemaVersion"] and receipt["schemaVersion"] == 1, "REPORT_SCHEMA")
        generator = run["generatorSha256"]
        matrix.require(matrix.hash_value(generator) and
                       report.get("execution") == {"sourceCommit": plan["sourceCommit"], "generatorSha256": generator} and
                       receipt.get("sourceCommit") == plan["sourceCommit"] and
                       receipt.get("generator") == {"name": "parser-cli", "schemaVersion": plan["reportSchemaVersion"], "sha256": generator} and
                       receipt.get("rawReportSha256") == run["report"]["sha256"], "PROVENANCE")
        rows = matrix.identities(report["results"], lambda r: r["result"]["sha256"])
        matrix.require(type(receipt.get("inputCount")) is int and receipt["inputCount"] == len(manifest) == len(rows), "INPUT_COUNT")
        matrix.require(set(rows) == set(manifest), "CONTROL_IDENTITY")
        binding = {"sourceCommit": plan["sourceCommit"], "sourceSha256": plan["source"]["sha256"],
                   "reportSha256": run["report"]["sha256"], "receiptSha256": run["receipt"]["sha256"], "generatorSha256": generator}
        matrix.require(len({run[k]["sha256"] for k in ("report", "evidence", "api", "oracle")}) == 4, "EVIDENCE_BINDING")
        matrix.local_path(run["cacheDir"], directory=True)
    documents = {}
    for name in ("evidence", "api", "oracle"):
        with gap(RUN_ROLES[name]):
            if name == "oracle":
                inputs.independent(run[name])
            doc = inputs.document(run[name])
            matrix.bound_document(doc, binding)
            matrix.require(isinstance(doc.get("controls"), dict) and set(doc["controls"]) == set(rows), "CONTROL_IDENTITY")
            documents[name] = doc["controls"]
    for identity, row in rows.items():
        c, evidence, api, oracle = controls[identity], documents["evidence"][identity], documents["api"][identity], documents["oracle"][identity]
        with gap("G0_SOURCE_BOUND_RUN"):
            matrix.require(all(row.get(k) is None for k in ("error", "catalogError", "persistenceError")) and
                           row.get("samples", {}).get("referenceErrors") == [], "REPORT_ERROR")
            result = row["result"]
            matrix.require(result.get("status") == "SELECTED" and result.get("selectedFamily") == c["family"], "LANGUAGE_AUTHORITY")
            probes = [p for p in result["probes"] if p.get("family") == c["family"]]
            matrix.require(len(probes) == 1, "LANGUAGE_AUTHORITY")
            matrix.language_manifest(probes[0]["resolvedLayout"]["languageManifest"], c)
            matrix.require(row.get("persistence", {}).get("fileName") == identity + ".sqlite", "CACHE_IDENTITY")
        with gap("G1_NORMALIZED_CHECKS"):
            matrix.require(matrix.hash_value(evidence.get("cacheSha256")), "CACHE_DIGEST")
            matrix.require(isinstance(evidence.get("checks"), dict) and set(evidence["checks"]) == matrix.CHECKS and
                           isinstance(oracle.get("checks"), dict) and set(oracle["checks"]) == matrix.CHECKS, "REQUIRED_CHECK")
        with gap("G2_CODEC_VECTORS"):
            vectors = evidence["checks"]["codecGoldenVectors"]["data"]
            matrix.require(isinstance(vectors, dict) and matrix.canonical(vectors) == matrix.canonical(oracle["checks"]["codecGoldenVectors"]) and
                           matrix.hash_value(vectors.get("vectorSetSha256")) and matrix.integer(vectors.get("vectorCount"), 1) and
                           type(vectors.get("matchedCount")) is int and vectors["matchedCount"] == vectors["vectorCount"], "REQUIRED_CHECK")
        with gap("G3_API_CAPTURE"):
            matrix.g3_capture(api, row, evidence, oracle, c, plan, binding)
        with gap("G1_NORMALIZED_CHECKS"):
            matrix.required_checks(evidence["checks"], oracle["checks"], api)
        with gap("G4_INDEPENDENT_ORACLE"):
            capability_metadata(row["catalog"]["localizedCapabilities"], api, oracle["capabilities"], inputs, binding, source, identity)
    return set(rows)


def prepare(request_ref, source_commit):
    inputs = MetadataInputs()
    with gap("G6_ASSEMBLY_REQUEST"):
        matrix.require(matrix.hash_value(source_commit, 40), "PROVENANCE")
        request = inputs.document(request_ref)
        exact_keys(request, ("schemaVersion", "expectations", "runs"))
        matrix.require(type(request["schemaVersion"]) is int and request["schemaVersion"] == 1, "INPUT_SCHEMA")
        matrix.require(isinstance(request["runs"], list) and 1 <= len(request["runs"]) <= 44, "INPUT_COUNT")
    with gap("G6_EXPECTATIONS"):
        plan = inputs.document(request["expectations"])
        exact_keys(plan, EXPECTATION_KEYS)
        matrix.require(type(plan["schemaVersion"]) is int and plan["schemaVersion"] == 1 and
                       all(matrix.integer(plan[k], 1) for k in ("reportSchemaVersion", "cacheSchemaVersion", "parserSchemaVersion")), "INPUT_SCHEMA")
        matrix.require(plan["sourceCommit"] == source_commit, "PROVENANCE")
        matrix.require(isinstance(plan["requiredSections"], list) and len(plan["requiredSections"]) == len(matrix.SECTIONS) and
                       set(plan["requiredSections"]) == matrix.SECTIONS, "CACHE_SCHEMA")
        controls = matrix.matrix_controls(plan)
        for c in controls.values():
            exact_keys(c, CONTROL_KEYS)
            matrix.require(isinstance(c["release"], str) and 0 < len(c["release"]) <= 128, "INPUT_SCHEMA")
    # Register all observed artifact roles before resolving any independent proof.
    for run in request["runs"]:
        for name, role in RUN_ROLES.items():
            with gap(role):
                matrix.require(isinstance(run, dict) and name in run, "INPUT_MISSING")
                key = inputs.reference(run[name], document=True)
                if name in ("report", "receipt", "evidence", "api"):
                    inputs.captured_paths.add(key)
                    inputs.captured_pins.add(run[name]["sha256"])
        with gap("G6_ASSEMBLY_REQUEST"):
            exact_keys(run, set(RUN_ROLES) | {"cacheDir", "generatorSha256"})
    with gap("G6_EXPECTATIONS"):
        inputs.independent(request["expectations"])
        inputs.independent(plan["source"])
        source = inputs.raw(plan["source"])
    selected = set()
    ordered = []
    for run in request["runs"]:
        ids = run_metadata(run, plan, controls, inputs, source)
        with gap("G0_SOURCE_BOUND_RUN"):
            matrix.require(not selected.intersection(ids), "CONTROL_IDENTITY")
        selected.update(ids)
        ordered.append((tuple(sorted(ids)), run))
    with gap("G0_SOURCE_BOUND_RUN"):
        matrix.require(selected == set(controls), "CONTROL_IDENTITY")
    # New containers only. Source documents and expected/captured values are never rewritten.
    return dict(plan, controls=sorted(controls.values(), key=lambda c: (c["family"], c["language"], c["sha256"])),
                requiredSections=sorted(plan["requiredSections"]), runs=[run for _, run in sorted(ordered)],
                assemblyInputs={"request": dict(request_ref), "expectations": dict(request["expectations"])})


def empty_result():
    return {"schemaVersion": 1, "status": "BLOCKED", "acceptance": False, "assembledControls": 0,
            "checkRecords": 0, "capabilityDispositions": 0, "deferredChecks": list(DEFERRED), "blockers": []}


def assemble(request_path, request_sha256, source_commit, output_path):
    """Assemble complete pinned metadata only; failure never exposes input text."""
    result = empty_result()
    try:
        with gap("G6_OUTPUT"):
            output = absolute_path(output_path)
            matrix.require(not output.exists() and not output.is_symlink(), "OUTPUT_EXISTS")
            matrix.require(output.parent.is_dir() and output.suffix.lower() == ".json", "OUTPUT_PATH")
        plan = prepare({"path": str(request_path), "sha256": request_sha256}, source_commit)
        with gap("G6_OUTPUT"):
            raw = matrix.canonical(plan) + b"\n"
            matrix.require(len(raw) <= matrix.MAX_JSON_BYTES, "INPUT_LIMIT")
            # Exclusive creation happens only after the whole metadata preflight succeeds.
            created = False
            try:
                with output.open("xb") as handle:
                    created = True
                    matrix.require(handle.write(raw) == len(raw), "OUTPUT_IO")
                    handle.flush()
            except (OSError, matrix.Blocked):
                # Includes close/flush failures, but never deletes a pre-existing
                # destination when exclusive creation itself lost a race.
                if created:
                    output.unlink()
                raise
        result.update(status="PLAN_ASSEMBLED", assembledControls=44, checkRecords=308,
                      capabilityDispositions=660, planSha256=matrix.digest(raw))
    except Refusal as error:
        result["blockers"] = [{"code": error.code, "gap": error.gap}]
    return result


def main(argv=None):
    parser = matrix.Arguments(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter, allow_abbrev=False)
    parser.add_argument("--request", required=True)
    parser.add_argument("--request-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True)
    try:
        args = parser.parse_args(argv)
        result = assemble(args.request, args.request_sha256, args.source_commit, args.output)
    except matrix.Blocked:
        result = empty_result()
        result["blockers"] = [{"code": "CLI_ARGUMENTS", "gap": "G6_ASSEMBLY_REQUEST"}]
    print(matrix.canonical(result).decode("utf-8"))
    return 0 if result["status"] == "PLAN_ASSEMBLED" else 1


if __name__ == "__main__":
    sys.exit(main())
