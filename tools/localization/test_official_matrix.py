"""Synthetic Task387 contract fixtures. Never reads an official ROM or real cache."""
import copy
from contextlib import closing
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

MODULE = Path(__file__).with_name("official_matrix.py")
FAMILIES = "RED_BLUE YELLOW GOLD_SILVER CRYSTAL RUBY_SAPPHIRE EMERALD FIRERED_LEAFGREEN".split()
CAPS = "SPECIES_NAMES SPECIES_DESCRIPTIONS MOVE_NAMES MOVE_DESCRIPTIONS ABILITY_NAMES ABILITY_DESCRIPTIONS TYPE_NAMES NATURE_NAMES ITEM_NAMES AREA_NAMES LOCAL_MAP_NAMES WORLD_REGION_NAMES WORLD_LOCATION_NAMES ENCOUNTER_AREA_NAMES POI_TEXT".split()
SECTIONS = "language_manifest species moves types abilities natures type_chart encounters capture_balls learnset_rulesets runtime_metadata world_maps trainer_assets local_maps theme capabilities diagnostics".split()
FIELDS = "speciesNames speciesDescriptions moveNames moveDescriptions abilityNames abilityDescriptions typeNames natureNames itemNames areaNames localMapNames worldRegionNames worldLocationNames encounterAreaNames poiTexts".split()
SOURCE = b"synthetic source evidence, not a production authority\n"
COMMIT = "a" * 40


def sha(data):
    return hashlib.sha256(data).hexdigest()


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


class Fixture:
    def __init__(self, root):
        self.root = Path(root)
        self.cache = self.root / "cache"
        self.cache.mkdir()
        self.plan = {"schemaVersion": 1, "sourceCommit": COMMIT,
                     "source": self.write("source.txt", SOURCE),
                     "reportSchemaVersion": 14, "cacheSchemaVersion": 2,
                     "parserSchemaVersion": 49, "requiredSections": SECTIONS, "controls": [], "runs": []}
        self.controls = self.plan["controls"]
        # These identities and codec IDs are explicitly synthetic expectations.
        for language in ["en", "fr", "de", "it", "es", "ja"]:
            for family in FAMILIES:
                self.control(language, family, "CONTROL")
        self.control("ko", "GOLD_SILVER", "GOLD")
        self.control("ko", "GOLD_SILVER", "SILVER")
        self.rows = []
        self.evidence = {"schemaVersion": 1, "controls": {}}
        self.api = {"schemaVersion": 1, "controls": {}}
        self.oracle = {"schemaVersion": 1, "controls": {}}
        self.proofs = {"schemaVersion": 1, "proofs": {}}
        for c in self.controls:
            self.make_control(c)
        self.report = {"schemaVersion": 14, "execution": {"sourceCommit": COMMIT,
                       "generatorSha256": "b" * 64}, "results": self.rows}
        self.receipt = {"schemaVersion": 1, "sourceCommit": COMMIT,
                        "generator": {"name": "parser-cli", "schemaVersion": 14, "sha256": "b" * 64},
                        "inputCount": 44}
        self.manifest = [{"sha256": c["sha256"], "family": c["family"],
                          "language": c["language"], "release": c["release"],
                          "privatePath": "PRIVATE_PATH_DO_NOT_PUBLISH"} for c in self.controls]
        self.refresh()

    def write(self, name, data):
        data = data if isinstance(data, bytes) else encoded(data)
        p = self.root / name
        p.write_bytes(data)
        return {"path": str(p), "sha256": sha(data)}

    def control(self, language, family, release):
        identity = sha((language + family + release).encode())
        self.controls.append({"sha256": identity, "language": language, "family": family,
                              "release": release, "codecId": "synthetic-" + language,
                              "codecVersion": 1})

    def make_control(self, c):
        identity, language = c["sha256"], c["language"]
        manifest = {"status": "RESOLVED", "defaultLanguage": language, "projections": [
            {"status": "RESOLVED", "language": language, "codecId": c["codecId"],
             "codecVersion": 1, "evidence": [{"kind": "COMPILED_CONSUMER"}],
             "localizedTables": {"speciesNames": {}, "moveNames": {}, "typeNames": {}}}]}
        caps = {k: {"status": "AVAILABLE", "coveredRecords": 1, "expectedRecords": 1} for k in CAPS}
        overlay = {"language": language, "overlayVersion": 1,
                   "localizedCapabilities": [dict(v, capability=k) for k, v in caps.items()]}
        api = {"bootstrap": {"romSha256": identity, "language": language,
                             "authority": "ROM_DEFAULT", "parserInvocations": 0}, "fields": {}}
        oracle_caps = {}
        for cap, field in zip(CAPS, FIELDS):
            value = "synthetic-" + language + "-" + field
            overlay[field] = {"1": {"value": value}}
            api["fields"][cap] = {"1": value}
            proof_id = identity + cap
            self.proofs["proofs"][proof_id] = {
                "kind": "FIELD_ACCEPTANCE", "romSha256": identity,
                "capability": cap, "recordIds": ["1"], "coveredRecords": 1, "expectedRecords": 1,
                "sourceSlice": {"offset": 0, "length": len(SOURCE), "sha256": sha(SOURCE)}}
            oracle_caps[cap] = {"disposition": "ACCEPTED", "coveredRecords": 1, "expectedRecords": 1,
                               "proof": {"pointer": "/proofs/" + proof_id}, "excluded": [],
                               "records": [{"id": "1", "cache": {"pointer": "/" + field + "/1/value", "value": value},
                                            "api": {"pointer": "/fields/" + cap + "/1", "value": value}}]}
        sections = {s: {} for s in SECTIONS}
        sections["language_manifest"] = manifest
        sections["language_overlay:" + language] = overlay
        path = self.cache / (identity + ".sqlite")
        with closing(sqlite3.connect(path)) as db, db:
            db.executescript("""
                CREATE TABLE catalog_metadata(id INTEGER, schema_version INTEGER, parser_schema_version INTEGER,
                    sha256 TEXT, family TEXT, is_complete INTEGER);
                CREATE TABLE catalog_sections(name TEXT PRIMARY KEY, encoding TEXT, payload BLOB);
                CREATE TABLE catalog_section_chunks(section_name TEXT, chunk_index INTEGER, payload BLOB,
                    PRIMARY KEY(section_name,chunk_index));
            """)
            db.execute("INSERT INTO catalog_metadata VALUES(1,2,49,?,?,1)", (identity, c["family"]))
            for name, value in sections.items():
                payload = gzip.compress(encoded(value), mtime=0)
                db.execute("INSERT INTO catalog_sections VALUES(?,?,?)", (name, "gzip+json+chunks-v1", bytes.fromhex(sha(payload))))
                db.execute("INSERT INTO catalog_section_chunks VALUES(?,0,?)", (name, payload))
        checks = {
            "rawHeader": {"rawHeaderSha256": "c" * 64, "byteCount": 80},
            "codecGoldenVectors": {"vectorSetSha256": "d" * 64, "vectorCount": 3, "matchedCount": 3},
            "structuralAuthority": {"consumerEvidenceSha256": sha(SOURCE), "corroboratingTableCount": 2},
            "reopenParity": {"beforeCatalogSha256": "e" * 64, "afterCatalogSha256": "e" * 64},
            "projectionIsolation": {"fieldsChecked": 15, "mixedFields": 0, "fallbackFields": 0, "sharedTextFields": 0},
            "typeSemantics": {"typesChecked": 3, "unresolvedTypes": 0, "mismatchedTypes": 0},
            "apiBootstrap": {"responseSha256": sha(encoded(api["bootstrap"])), "parserInvocations": 0},
        }
        self.evidence["controls"][identity] = {
            "cacheSha256": sha(path.read_bytes()),
            "checks": {k: {"tests": 1, "failures": 0, "errors": 0, "skipped": 0, "data": v} for k, v in checks.items()}}
        api["cacheSha256"] = sha(path.read_bytes())
        self.api["controls"][identity] = api
        self.oracle["controls"][identity] = {
            "checks": copy.deepcopy(checks), "capabilities": oracle_caps,
            "bootstrap": {"romSha256": {"pointer": "/romSha256", "value": identity},
                          "language": {"pointer": "/language", "value": language},
                          "authority": {"pointer": "/authority", "value": "ROM_DEFAULT"},
                          "parserInvocations": {"pointer": "/parserInvocations", "value": 0}}}
        self.rows.append({"result": {"sha256": identity, "status": "SELECTED", "selectedFamily": c["family"],
                                     "probes": [{"family": c["family"], "resolvedLayout": {"languageManifest": manifest}}]},
                          "catalog": {"localizedCapabilities": caps}, "samples": {"referenceErrors": []},
                          "persistence": {"fileName": identity + ".sqlite"},
                          "catalogError": None, "persistenceError": None, "error": None})

    def refresh(self):
        report = self.write("report.json", self.report)
        self.receipt["rawReportSha256"] = report["sha256"]
        receipt = self.write("receipt.json", self.receipt)
        binding = {"sourceCommit": COMMIT, "sourceSha256": sha(SOURCE),
                   "reportSha256": report["sha256"], "receiptSha256": receipt["sha256"],
                   "generatorSha256": "b" * 64}
        for obj in (self.evidence, self.api, self.oracle, self.proofs):
            obj["binding"] = copy.deepcopy(binding)
        proofs = self.write("proofs.json", self.proofs)
        for control in self.oracle["controls"].values():
            for cap in control["capabilities"].values():
                cap["proof"]["artifact"] = proofs
                for exclusion in cap["excluded"]:
                    exclusion["proof"]["artifact"] = proofs
        self.plan["runs"] = [{"manifest": self.write("manifest.json", self.manifest),
                              "report": report, "receipt": receipt,
                              "evidence": self.write("evidence.json", self.evidence),
                              "api": self.write("api.json", self.api),
                              "oracle": self.write("oracle.json", self.oracle),
                              "cacheDir": str(self.cache), "generatorSha256": "b" * 64}]
        return self.save_plan()

    def save_plan(self):
        self.pin = self.write("plan.json", self.plan)
        return self.pin

    def mutate_cache(self, sql, params=()):
        identity = self.controls[0]["sha256"]
        p = self.cache / (identity + ".sqlite")
        with closing(sqlite3.connect(p)) as db, db:
            db.execute(sql, params)
        self.evidence["controls"][identity]["cacheSha256"] = sha(p.read_bytes())
        self.api["controls"][identity]["cacheSha256"] = sha(p.read_bytes())
        self.refresh()


    def coverage(self, total, covered, disposition="ACCEPTED"):
        """Vary one synthetic optional field; retain independent sample values."""
        identity = self.controls[0]["sha256"]
        cap = "ITEM_NAMES"
        state = self.rows[0]["catalog"]["localizedCapabilities"][cap]
        state.update(expectedRecords=total, coveredRecords=covered,
                     status="AVAILABLE" if covered == total and covered else "PARTIAL" if covered else "NOT_FOUND")
        accept = self.oracle["controls"][identity]["capabilities"][cap]
        accept.update(expectedRecords=total, coveredRecords=covered, disposition=disposition)
        proof = self.proofs["proofs"][identity + cap]
        proof.update(expectedRecords=total, coveredRecords=covered)
        if not covered:
            accept["records"] = []
            accept["absence"] = {"cache": {"pointer": "/itemNames", "value": {}},
                                 "api": {"pointer": "/fields/ITEM_NAMES", "value": {}}}
            proof.update(kind="NOT_APPLICABLE", recordIds=[])
            self.api["controls"][identity]["fields"][cap] = {}
        path = self.cache / (identity + ".sqlite")
        name = "language_overlay:" + self.controls[0]["language"]
        with closing(sqlite3.connect(path)) as db, db:
            raw = db.execute("SELECT payload FROM catalog_section_chunks WHERE section_name=?", (name,)).fetchone()[0]
            overlay = json.loads(gzip.decompress(raw))
            for c in overlay["localizedCapabilities"]:
                if c["capability"] == cap:
                    c.update(state)
            if not covered:
                overlay["itemNames"] = {}
            raw = gzip.compress(encoded(overlay), mtime=0)
            db.execute("UPDATE catalog_section_chunks SET payload=? WHERE section_name=?", (raw, name))
            db.execute("UPDATE catalog_sections SET payload=? WHERE name=?", (bytes.fromhex(sha(raw)), name))
        self.evidence["controls"][identity]["cacheSha256"] = sha(path.read_bytes())
        self.api["controls"][identity]["cacheSha256"] = sha(path.read_bytes())
        self.refresh()

    def split_runs(self):
        runs = []
        for index, selected in enumerate((self.controls[:35], self.controls[35:])):
            ids = {c["sha256"] for c in selected}
            report = copy.deepcopy(self.report)
            report["results"] = [r for r in report["results"] if r["result"]["sha256"] in ids]
            report_ref = self.write(f"report-{index}.json", report)
            receipt = copy.deepcopy(self.receipt)
            receipt.update(inputCount=len(ids), rawReportSha256=report_ref["sha256"])
            receipt_ref = self.write(f"receipt-{index}.json", receipt)
            binding = copy.deepcopy(self.evidence["binding"])
            binding.update(reportSha256=report_ref["sha256"], receiptSha256=receipt_ref["sha256"])
            proofs = copy.deepcopy(self.proofs)
            proofs["binding"] = binding
            proof_ref = self.write(f"proofs-{index}.json", proofs)
            run = {"manifest": self.write(f"manifest-{index}.json", [c for c in self.manifest if c["sha256"] in ids]),
                   "report": report_ref, "receipt": receipt_ref, "cacheDir": str(self.cache), "generatorSha256": "b" * 64}
            for name in ("evidence", "api", "oracle"):
                obj = copy.deepcopy(getattr(self, name))
                obj["binding"] = binding
                obj["controls"] = {k: v for k, v in obj["controls"].items() if k in ids}
                if name == "oracle":
                    for control in obj["controls"].values():
                        for cap in control["capabilities"].values():
                            cap["proof"]["artifact"] = proof_ref
                run[name] = self.write(f"{name}-{index}.json", obj)
            runs.append(run)
        self.plan["runs"] = runs
        self.save_plan()


class MatrixTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(MODULE.is_file(), "Task387 implementation has not been created")
        spec = importlib.util.spec_from_file_location("official_matrix", MODULE)
        self.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.mod)
        self.temp = tempfile.TemporaryDirectory(prefix="task387-synthetic-")
        self.addCleanup(self.temp.cleanup)
        self.fx = Fixture(self.temp.name)
        self.identity = self.fx.controls[0]["sha256"]

    def validate(self):
        return self.mod.validate(Path(self.fx.pin["path"]), self.fx.pin["sha256"], COMMIT)

    def blocked(self, code=None):
        result = self.validate()
        self.assertEqual(result["status"], "BLOCKED", result["blockers"])
        self.assertEqual(result["validatedControls"], 0)
        if code:
            self.assertIn(code, [b["code"] for b in result["blockers"]], result)
        return result

    def test_complete_synthetic_matrix(self):
        result = self.validate()
        self.assertEqual(result["status"], "EVIDENCE_VALIDATED", result)
        self.assertEqual((result["validatedCells"], result["validatedControls"]), (43, 44))
        self.assertEqual(len(result["controls"]), 44)
        self.assertTrue(all(len(c["capabilities"]) == 15 for c in result["controls"]))

    def test_duplicate_expected_hash(self):
        self.fx.controls[1]["sha256"] = self.identity
        self.fx.save_plan()
        self.blocked("CONTROL_IDENTITY")

    def test_missing_expected_control(self):
        self.fx.controls.pop()
        self.fx.save_plan()
        self.blocked("MATRIX_COVERAGE")

    def test_duplicate_report_hash(self):
        self.fx.rows[1] = copy.deepcopy(self.fx.rows[0])
        self.fx.refresh()
        self.blocked("CONTROL_IDENTITY")

    def test_missing_report_hash(self):
        self.fx.rows.pop()
        self.fx.refresh()
        self.blocked()

    def test_substituted_report_hash(self):
        self.fx.rows[0]["result"]["sha256"] = "f" * 64
        self.fx.refresh()
        self.blocked("CONTROL_IDENTITY")

    def test_substituted_manifest_hash(self):
        self.fx.manifest[0]["sha256"] = "f" * 64
        self.fx.refresh()
        self.blocked("CONTROL_IDENTITY")

    def test_duplicate_korean_gold_is_not_silver(self):
        self.fx.controls[-1]["release"] = "GOLD"
        self.fx.save_plan()
        self.blocked("MATRIX_COVERAGE")

    def test_wrong_korean_cell(self):
        self.fx.controls[-1]["family"] = "CRYSTAL"
        self.fx.save_plan()
        self.blocked("MATRIX_COVERAGE")

    def test_bad_plan_pin(self):
        self.fx.pin["sha256"] = "0" * 64
        self.blocked("ARTIFACT_DIGEST")

    def test_stale_report_source(self):
        self.fx.report["execution"]["sourceCommit"] = "c" * 40
        self.fx.refresh()
        self.blocked("PROVENANCE")

    def test_stale_receipt_source(self):
        self.fx.receipt["sourceCommit"] = "c" * 40
        self.fx.refresh()
        self.blocked("PROVENANCE")

    def test_source_artifact_tampering(self):
        Path(self.fx.plan["source"]["path"]).write_bytes(b"changed source")
        self.blocked("ARTIFACT_DIGEST")

    def test_wrong_input_count(self):
        self.fx.receipt["inputCount"] = 43
        self.fx.refresh()
        self.blocked("INPUT_COUNT")

    def test_report_digest_tampering(self):
        Path(self.fx.plan["runs"][0]["report"]["path"]).write_bytes(b"{}")
        self.blocked("ARTIFACT_DIGEST")

    def test_receipt_digest_tampering(self):
        Path(self.fx.plan["runs"][0]["receipt"]["path"]).write_bytes(b"{}")
        self.blocked("ARTIFACT_DIGEST")

    def test_evidence_source_mixing(self):
        self.fx.evidence["binding"]["sourceSha256"] = "f" * 64
        self.fx.plan["runs"][0]["evidence"] = self.fx.write("evidence.json", self.fx.evidence)
        self.fx.save_plan()
        self.blocked("EVIDENCE_BINDING")

    def test_api_source_mixing(self):
        self.fx.api["binding"]["reportSha256"] = "f" * 64
        self.fx.plan["runs"][0]["api"] = self.fx.write("api.json", self.fx.api)
        self.fx.save_plan()
        self.blocked("EVIDENCE_BINDING")

    def test_oracle_source_mixing(self):
        self.fx.oracle["binding"]["receiptSha256"] = "f" * 64
        self.fx.plan["runs"][0]["oracle"] = self.fx.write("oracle.json", self.fx.oracle)
        self.fx.save_plan()
        self.blocked("EVIDENCE_BINDING")

    def test_missing_required_check(self):
        del self.fx.evidence["controls"][self.identity]["checks"]["projectionIsolation"]
        self.fx.refresh()
        self.blocked("REQUIRED_CHECK")

    def test_required_check_skipped(self):
        self.fx.evidence["controls"][self.identity]["checks"]["rawHeader"]["skipped"] = 1
        self.fx.refresh()
        self.blocked("REQUIRED_CHECK")

    def test_free_form_pass_not_evidence(self):
        self.fx.evidence["controls"][self.identity]["checks"]["rawHeader"]["data"] = "pass"
        self.fx.refresh()
        self.blocked("REQUIRED_CHECK")

    def test_contamination_cannot_be_oracle_waived(self):
        self.fx.evidence["controls"][self.identity]["checks"]["projectionIsolation"]["data"]["fallbackFields"] = 1
        self.fx.oracle["controls"][self.identity]["checks"]["projectionIsolation"]["fallbackFields"] = 1
        self.fx.refresh()
        self.blocked("REQUIRED_CHECK")

    def test_missing_api_capture(self):
        del self.fx.api["controls"][self.identity]
        self.fx.refresh()
        self.blocked("CONTROL_IDENTITY")

    def test_independent_api_field_mismatch(self):
        self.fx.api["controls"][self.identity]["fields"]["SPECIES_NAMES"]["1"] = "wrong"
        self.fx.refresh()
        self.blocked("FIELD_ACCEPTANCE")

    def test_missing_capability(self):
        del self.fx.oracle["controls"][self.identity]["capabilities"]["ITEM_NAMES"]
        self.fx.refresh()
        self.blocked("CAPABILITY_INVENTORY")

    def test_invalid_capability_count(self):
        self.fx.rows[0]["catalog"]["localizedCapabilities"]["SPECIES_NAMES"]["coveredRecords"] = 2
        self.fx.refresh()
        self.blocked("CAPABILITY_COUNTS")

    def test_boolean_is_not_count(self):
        self.fx.rows[0]["catalog"]["localizedCapabilities"]["SPECIES_NAMES"]["coveredRecords"] = True
        self.fx.refresh()
        self.blocked("CAPABILITY_COUNTS")

    def test_unsupported_exclusion(self):
        cap = self.fx.oracle["controls"][self.identity]["capabilities"]["ITEM_NAMES"]
        cap["disposition"] = "NOT_FOUND"
        self.fx.refresh()
        self.blocked("CAPABILITY_DISPOSITION")

    def test_missing_independent_acceptance_proof(self):
        cap = self.fx.oracle["controls"][self.identity]["capabilities"]["SPECIES_NAMES"]
        cap["proof"]["pointer"] = "/missing"
        self.fx.refresh()
        self.blocked("EVIDENCE_REFERENCE")

    def test_proof_cannot_be_pass_string(self):
        self.fx.proofs["proofs"][self.identity + "SPECIES_NAMES"] = "pass"
        self.fx.refresh()
        self.blocked("EVIDENCE_REFERENCE")

    def test_cache_digest_tampering(self):
        p = self.fx.cache / (self.identity + ".sqlite")
        p.write_bytes(b"corrupt")
        self.blocked("CACHE_DIGEST")

    def test_corrupt_cache_with_updated_pin(self):
        p = self.fx.cache / (self.identity + ".sqlite")
        p.write_bytes(b"corrupt")
        self.fx.evidence["controls"][self.identity]["cacheSha256"] = sha(b"corrupt")
        self.fx.refresh()
        self.blocked("CACHE_INVALID")

    def test_cache_schema_mismatch(self):
        self.fx.mutate_cache("UPDATE catalog_metadata SET parser_schema_version=48")
        self.blocked("CACHE_SCHEMA")

    def test_cache_identity_mismatch(self):
        self.fx.mutate_cache("UPDATE catalog_metadata SET sha256=?", ("f" * 64,))
        self.blocked("CACHE_SCHEMA")

    def test_cache_section_missing(self):
        self.fx.mutate_cache("DELETE FROM catalog_sections WHERE name='species'")
        self.blocked("CACHE_SECTIONS")

    def test_cache_chunk_digest_mismatch(self):
        self.fx.mutate_cache("UPDATE catalog_sections SET payload=? WHERE name='species'", (b"x" * 32,))
        self.blocked("CACHE_SECTION_DIGEST")

    def test_cache_chunk_gap(self):
        self.fx.mutate_cache("UPDATE catalog_section_chunks SET chunk_index=2 WHERE section_name='species'")
        self.blocked("CACHE_CHUNKS")

    def test_oversized_json_is_bounded(self):
        p = self.fx.root / "oversized.json"
        p.write_bytes(b" " * (self.mod.MAX_JSON_BYTES + 1))
        self.fx.plan["runs"][0]["api"] = {"path": str(p), "sha256": sha(p.read_bytes())}
        self.fx.save_plan()
        self.blocked("INPUT_LIMIT")

    def test_duplicate_json_keys_are_rejected(self):
        p = self.fx.write("bad.json", b'{"schemaVersion":1,"schemaVersion":1}')
        self.fx.plan["runs"][0]["api"] = p
        self.fx.save_plan()
        self.blocked("INPUT_SCHEMA")

    def test_success_output_is_allowlisted(self):
        self.fx.rows[0]["privateDiagnostic"] = "PRIVATE_PATH_DO_NOT_PUBLISH"
        self.fx.refresh()
        text = json.dumps(self.validate())
        for forbidden in [self.temp.name, "PRIVATE_PATH", "synthetic-en-speciesNames", "sourceSlice", "codecId"]:
            self.assertNotIn(forbidden, text)

    def test_failure_output_is_allowlisted(self):
        self.fx.rows[0]["catalogError"] = "PRIVATE_PATH_DO_NOT_PUBLISH secret prose"
        self.fx.refresh()
        text = json.dumps(self.blocked())
        self.assertNotIn("PRIVATE_PATH", text)
        self.assertNotIn("secret prose", text)
        self.assertNotIn(self.temp.name, text)

    def test_two_run_western_native_partition(self):
        self.fx.split_runs()
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")

    def test_samples_do_not_require_full_catalog_oracle(self):
        self.fx.coverage(2, 2)
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")

    def test_source_proven_zero_count_exclusion(self):
        self.fx.coverage(0, 0, "EXCLUDED")
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")

    def test_exclusion_without_fail_closed_observations(self):
        self.fx.coverage(0, 0, "EXCLUDED")
        del self.fx.oracle["controls"][self.identity]["capabilities"]["ITEM_NAMES"]["absence"]
        self.fx.refresh()
        self.blocked("FIELD_ACCEPTANCE")

    def test_exclusion_cannot_hide_api_text(self):
        self.fx.coverage(0, 0, "EXCLUDED")
        self.fx.api["controls"][self.identity]["fields"]["ITEM_NAMES"] = {"1": "unexpected borrowed text"}
        self.fx.refresh()
        self.blocked("FIELD_ACCEPTANCE")

    def test_boolean_schema_version(self):
        self.fx.plan["schemaVersion"] = True
        self.fx.save_plan()
        self.blocked("INPUT_SCHEMA")

    def test_explicit_skipped_status_cannot_contradict_counts(self):
        self.fx.evidence["controls"][self.identity]["checks"]["rawHeader"]["status"] = "SKIPPED"
        self.fx.refresh()
        self.blocked("REQUIRED_CHECK")

    def test_boolean_receipt_schema_version(self):
        self.fx.receipt["schemaVersion"] = True
        self.fx.refresh()
        self.blocked("REPORT_SCHEMA")

    def test_corrupt_gzip_deflate_is_sanitized(self):
        invalid = bytes.fromhex("1f8b08000000000000ff07") + b"x" * 10
        p = self.fx.cache / (self.identity + ".sqlite")
        with closing(sqlite3.connect(p)) as db, db:
            db.execute("UPDATE catalog_section_chunks SET payload=? WHERE section_name='species'", (invalid,))
        self.fx.mutate_cache("UPDATE catalog_sections SET payload=? WHERE name='species'", (bytes.fromhex(sha(invalid)),))
        self.blocked("CACHE_INVALID")

    def test_api_capture_stale_cache_identity(self):
        self.fx.api["controls"][self.identity]["cacheSha256"] = "f" * 64
        self.fx.refresh()
        self.blocked("EVIDENCE_BINDING")

    def test_proof_stale_report_identity(self):
        self.fx.proofs["binding"]["reportSha256"] = "f" * 64
        ref = self.fx.write("proofs.json", self.fx.proofs)
        for control in self.fx.oracle["controls"].values():
            for cap in control["capabilities"].values():
                cap["proof"]["artifact"] = ref
        self.fx.plan["runs"][0]["oracle"] = self.fx.write("oracle.json", self.fx.oracle)
        self.fx.save_plan()
        self.blocked("EVIDENCE_BINDING")

    def test_unaccounted_partial_coverage_blocks(self):
        self.fx.coverage(2, 1)
        self.blocked("CAPABILITY_COUNTS")

    def test_oversized_inflate_is_bounded(self):
        payload = gzip.compress(b" " * (self.mod.MAX_JSON_BYTES + 1), mtime=0)
        p = self.fx.cache / (self.identity + ".sqlite")
        with closing(sqlite3.connect(p)) as db, db:
            db.execute("UPDATE catalog_section_chunks SET payload=? WHERE section_name='species'", (payload,))
        self.fx.mutate_cache("UPDATE catalog_sections SET payload=? WHERE name='species'", (bytes.fromhex(sha(payload)),))
        self.blocked("INPUT_LIMIT")

    def test_cli_argument_errors_are_sanitized(self):
        process = subprocess.run([sys.executable, "-B", str(MODULE), "--plan", "PRIVATE_PATH_DO_NOT_PUBLISH",
                                  "--plan-sha256", "0" * 64, "--source-commit", COMMIT, "--unexpected", "SECRET_PROSE"],
                                 capture_output=True, text=True, timeout=10)
        self.assertEqual(process.returncode, 1)
        self.assertNotIn("PRIVATE_PATH", process.stdout + process.stderr)
        self.assertNotIn("SECRET_PROSE", process.stdout + process.stderr)
        self.assertEqual(json.loads(process.stdout)["status"], "BLOCKED")

    def test_cli_success_and_missing_input_exit_codes(self):
        command = [sys.executable, "-B", str(MODULE), "--plan", self.fx.pin["path"],
                   "--plan-sha256", self.fx.pin["sha256"], "--source-commit", COMMIT]
        process = subprocess.run(command, capture_output=True, text=True, timeout=20)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(json.loads(process.stdout)["validatedControls"], 44)
        command[4] = str(self.fx.root / "PRIVATE_PATH_MISSING")
        process = subprocess.run(command, capture_output=True, text=True, timeout=10)
        self.assertEqual(process.returncode, 1)
        self.assertEqual(json.loads(process.stdout)["status"], "BLOCKED")
        self.assertNotIn("PRIVATE_PATH", process.stdout + process.stderr)

    def test_supported_reserved_slot_partial_coverage(self):
        self.fx.coverage(2, 1)
        key = self.identity + "ITEM_NAMES"
        proof = copy.deepcopy(self.fx.proofs["proofs"][key])
        proof.update(kind="RESERVED_SLOT", recordIds=["2"])
        self.fx.proofs["proofs"][key + "-excluded"] = proof
        self.fx.oracle["controls"][self.identity]["capabilities"]["ITEM_NAMES"]["excluded"] = [
            {"id": "2", "proof": {"pointer": "/proofs/" + key + "-excluded"}}]
        self.fx.refresh()
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")

    def test_actual_not_applicable_status_supported(self):
        self.fx.coverage(0, 0, "EXCLUDED")
        self.fx.rows[0]["catalog"]["localizedCapabilities"]["ITEM_NAMES"]["status"] = "NOT_APPLICABLE"
        path = self.fx.cache / (self.identity + ".sqlite")
        name = "language_overlay:en"
        with closing(sqlite3.connect(path)) as db, db:
            raw = db.execute("SELECT payload FROM catalog_section_chunks WHERE section_name=?", (name,)).fetchone()[0]
            obj = json.loads(gzip.decompress(raw))
            next(c for c in obj["localizedCapabilities"] if c["capability"] == "ITEM_NAMES")["status"] = "NOT_APPLICABLE"
            raw = gzip.compress(encoded(obj), mtime=0)
            db.execute("UPDATE catalog_section_chunks SET payload=? WHERE section_name=?", (raw, name))
        self.fx.mutate_cache("UPDATE catalog_sections SET payload=? WHERE name=?", (bytes.fromhex(sha(raw)), name))
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")

    def test_source_slice_cannot_be_unchecked_hash(self):
        self.fx.proofs["proofs"][self.identity + "SPECIES_NAMES"]["sourceSlice"]["sha256"] = "f" * 64
        self.fx.refresh()
        self.blocked("EVIDENCE_REFERENCE")

    def test_database_views_cannot_replace_schema_tables(self):
        self.fx.mutate_cache("ALTER TABLE catalog_metadata RENAME TO fake_metadata")
        self.fx.mutate_cache("CREATE VIEW catalog_metadata AS SELECT * FROM fake_metadata")
        self.blocked("CACHE_SCHEMA")

    def test_read_only_cache_has_no_sidecars_or_changes(self):
        before = {p.name: sha(p.read_bytes()) for p in self.fx.cache.iterdir()}
        self.assertEqual(self.validate()["status"], "EVIDENCE_VALIDATED")
        after = {p.name: sha(p.read_bytes()) for p in self.fx.cache.iterdir()}
        self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main(verbosity=2)
