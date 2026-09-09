"""G6 synthetic metadata tests; no ROMs, SQLite payloads or real proof fixtures."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import official_matrix as matrix
from test_official_matrix import bind_fixture_capture, fixture_bootstrap

MODULE = Path(__file__).with_name("build_official_matrix_plan.py")
COMMIT = "a" * 40
SOURCE = b"FABRICATED independent source slice for assembly tests only\n"
GENERATOR = "b" * 64


def sha(data):
    return hashlib.sha256(data).hexdigest()


def bootstrap(c):
    return fixture_bootstrap(c, None, None)


def logical_digest(c):
    return sha(("fabricated logical catalog " + c["sha256"]).encode())


def declared_checks(c):
    """Explicit synthetic declarations, never extracted from a production result."""
    return {
        "rawHeader": {"rawHeaderSha256": "c" * 64, "byteCount": 80},
        "codecGoldenVectors": {"vectorSetSha256": "d" * 64, "vectorCount": 3, "matchedCount": 3},
        "structuralAuthority": {"consumerEvidenceSha256": sha(SOURCE), "corroboratingTableCount": 2},
        "reopenParity": {"beforeCatalogSha256": logical_digest(c), "afterCatalogSha256": logical_digest(c)},
        "projectionIsolation": {"fieldsChecked": 15, "mixedFields": 0, "fallbackFields": 0, "sharedTextFields": 0},
        "typeSemantics": {"typesChecked": 3, "unresolvedTypes": 0, "mismatchedTypes": 0},
        "apiBootstrap": {"responseSha256": sha(matrix.canonical(bootstrap(c))), "parserInvocations": 0},
    }


class SyntheticInputs:
    """Entirely fabricated JSON. The cache directory is deliberately empty."""
    def __init__(self, root, split=False):
        self.root = Path(root)
        self.cache = self.root / "cache"
        self.cache.mkdir()
        controls = []
        cells = [(f, lang, "TEST") for lang in ("en", "fr", "de", "it", "es", "ja")
                 for f in sorted(matrix.FAMILIES)] + [("GOLD_SILVER", "ko", r) for r in ("GOLD", "SILVER")]
        for family, language, release in cells:
            controls.append({"sha256": sha((family + language + release).encode()), "family": family,
                             "language": language, "release": release, "codecId": "fabricated-" + language,
                             "codecVersion": 1})
        self.expectations = {"schemaVersion": 1, "sourceCommit": COMMIT,
                             "source": self.write("source.txt", SOURCE), "reportSchemaVersion": 16,
                             "cacheSchemaVersion": 2, "parserSchemaVersion": 64,
                             "requiredSections": sorted(matrix.SECTIONS), "controls": controls}
        groups = ([controls[:35], controls[35:]] if split else [controls])
        self.data = [self.make_run(group) for group in groups]
        self.request = {"schemaVersion": 1, "runs": []}
        self.refresh()

    def write(self, name, data):
        raw = data if isinstance(data, bytes) else matrix.canonical(data)
        path = self.root / name
        path.write_bytes(raw)
        return {"path": str(path), "sha256": sha(raw)}

    def make_run(self, controls):
        d = {k: {"schemaVersion": 1, "controls": {}} for k in ("evidence", "api", "oracle")}
        d.update(manifest=copy.deepcopy(controls), proofs={"schemaVersion": 1, "proofs": {}},
                 report={"schemaVersion": 16, "execution": {"sourceCommit": COMMIT, "generatorSha256": GENERATOR}, "results": []},
                 receipt={"schemaVersion": 1, "sourceCommit": COMMIT, "inputCount": len(controls),
                          "generator": {"name": "parser-cli", "schemaVersion": 16, "sha256": GENERATOR}})
        for c in controls:
            identity = c["sha256"]
            language_manifest = {"status": "RESOLVED", "defaultLanguage": c["language"], "projections": [
                {"status": "RESOLVED", "language": c["language"], "codecId": c["codecId"], "codecVersion": 1,
                 "evidence": [{"kind": "FABRICATED_TEST_ONLY"}]}]}
            d["report"]["results"].append({"result": {"sha256": identity, "status": "SELECTED", "selectedFamily": c["family"],
                "probes": [{"family": c["family"], "resolvedLayout": {"languageManifest": language_manifest}}]},
                "samples": {"referenceErrors": []}, "persistence": {"fileName": identity + ".sqlite",
                    "logicalDigestVersion": 1, "beforeCatalogSha256": logical_digest(c), "afterCatalogSha256": logical_digest(c)},
                "catalog": {"localizedCapabilities": {cap: {"status": "AVAILABLE", "coveredRecords": 1, "expectedRecords": 1}
                                                      for cap in matrix.CAPABILITIES}}})
            d["evidence"]["controls"][identity] = {"cacheSha256": "f" * 64, "checks": {
                name: {"tests": 1, "failures": 0, "errors": 0, "skipped": 0, "data": value}
                for name, value in declared_checks(c).items()}}
            # Fake captured values and independently declared expectations are separate inputs.
            d["api"]["controls"][identity] = {"acceptance": False, "scope": "CACHE_ONLY_OBSERVATION",
                "cacheSha256": "f" * 64, "catalogLogicalDigest": {"version": 1, "sha256": logical_digest(c)},
                "bootstrap": bootstrap(c),
                "fields": {cap: {"1": "fabricated-" + c["language"] + "-" + cap} for cap in matrix.CAPABILITIES}}
            oracle = {"checks": declared_checks(c), "bootstrap": {
                "romSha256": {"pointer": "/response/catalog/hash", "value": identity},
                "language": {"pointer": "/response/language/activeLanguage", "value": c["language"]},
                "authority": {"pointer": "/response/language/authority", "value": "ROM_DEFAULT"},
                "parserInvocations": {"pointer": "/parserInvocations", "value": 0}}, "capabilities": {}}
            for cap in matrix.CAPABILITIES:
                key = identity + cap
                d["proofs"]["proofs"][key] = {"kind": "FIELD_ACCEPTANCE", "romSha256": identity, "capability": cap,
                    "recordIds": ["1"], "coveredRecords": 1, "expectedRecords": 1,
                    "sourceSlice": {"offset": 0, "length": len(SOURCE), "sha256": sha(SOURCE)}}
                value = "fabricated-" + c["language"] + "-" + cap
                oracle["capabilities"][cap] = {"disposition": "ACCEPTED", "coveredRecords": 1, "expectedRecords": 1,
                    "proof": {"pointer": "/proofs/" + key}, "excluded": [],
                    "records": [{"id": "1", "cache": {"pointer": "/" + cap + "/1", "value": value},
                                 "api": {"pointer": "/fields/" + cap + "/1", "value": value}}]}
            d["oracle"]["controls"][identity] = oracle
        return d

    def refresh(self):
        self.request["expectations"] = self.write("expectations.json", self.expectations)
        self.request["runs"] = []
        for index, data in enumerate(self.data):
            report = self.write(f"report-{index}.json", data["report"])
            data["receipt"]["rawReportSha256"] = report["sha256"]
            receipt = self.write(f"receipt-{index}.json", data["receipt"])
            binding = {"sourceCommit": COMMIT, "sourceSha256": self.expectations["source"]["sha256"],
                       "reportSha256": report["sha256"], "receiptSha256": receipt["sha256"], "generatorSha256": GENERATOR}
            for name in ("evidence", "api", "oracle", "proofs"):
                data[name]["binding"] = dict(binding)
            for c in data["manifest"]:
                identity = c["sha256"]
                if identity in data["api"]["controls"]:
                    bind_fixture_capture(c, data["api"]["controls"][identity], data["evidence"]["controls"][identity],
                                         data["oracle"]["controls"][identity], binding)
            proofs = self.write(f"proofs-{index}.json", data["proofs"])
            for c in data["oracle"]["controls"].values():
                for cap in c["capabilities"].values():
                    cap["proof"]["artifact"] = proofs
                    for excluded in cap["excluded"]:
                        excluded["proof"]["artifact"] = proofs
            run = {"report": report, "receipt": receipt, "cacheDir": str(self.cache), "generatorSha256": GENERATOR}
            run.update({name: self.write(f"{name}-{index}.json", data[name]) for name in ("manifest", "evidence", "api", "oracle")})
            self.request["runs"].append(run)
        self.save()

    def save(self):
        self.pin = self.write("request.json", self.request)

    def repin_document(self, name, index=0):
        self.request["runs"][index][name] = self.write(f"{name}-{index}.json", self.data[index][name])
        self.save()


class AssemblyTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(MODULE.is_file(), "G6 real metadata assembly adapter is not implemented")
        spec = importlib.util.spec_from_file_location("build_official_matrix_plan", MODULE)
        self.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.mod)
        self.temp = tempfile.TemporaryDirectory(prefix="g6-synthetic-")
        self.addCleanup(self.temp.cleanup)
        self.fx = SyntheticInputs(self.temp.name)
        self.output = self.fx.root / "assembled.json"
        self.identity = self.fx.expectations["controls"][0]["sha256"]

    def assemble(self):
        with patch.object(matrix, "read_cache", side_effect=AssertionError("cache payload access forbidden")), \
             patch.object(matrix, "validate", side_effect=AssertionError("acceptance validation forbidden")):
            return self.mod.assemble(self.fx.pin["path"], self.fx.pin["sha256"], COMMIT, str(self.output))

    def blocked(self, gap=None):
        result = self.assemble()
        self.assertEqual(result["status"], "BLOCKED", result)
        self.assertFalse(result["acceptance"])
        self.assertEqual(result["assembledControls"], 0)
        self.assertFalse(self.output.exists())
        if gap:
            self.assertIn(gap, [b["gap"] for b in result["blockers"]])
        text = json.dumps(result)
        self.assertNotIn(self.temp.name, text)
        self.assertNotIn(self.identity, text)
        return result

    def test_complete_metadata_assembles_without_any_cache_files(self):
        result = self.assemble()
        self.assertEqual(result["status"], "PLAN_ASSEMBLED", result)
        self.assertFalse(result["acceptance"])
        self.assertEqual((result["assembledControls"], result["checkRecords"], result["capabilityDispositions"]), (44, 308, 660))
        self.assertIn("CACHE_PAYLOAD_VALIDATION", result["deferredChecks"])
        raw = self.output.read_bytes()
        self.assertEqual(result["planSha256"], sha(raw))
        plan = matrix.parse_json(raw)
        self.assertEqual(len(matrix.matrix_controls(plan)), 44)
        self.assertEqual(raw, matrix.canonical(plan) + b"\n")
        self.assertEqual(plan["source"], self.fx.expectations["source"])
        self.assertEqual(plan["assemblyInputs"]["request"], self.fx.pin)
        self.assertEqual(list(self.fx.cache.iterdir()), [])
        self.assertNotIn("fabricated-en-", raw.decode())

    def test_deterministic_output_and_inputs_unchanged(self):
        before = {p.name: p.read_bytes() for p in self.fx.root.iterdir() if p.is_file()}
        self.assertEqual(self.assemble()["status"], "PLAN_ASSEMBLED")
        first = self.output.read_bytes()
        self.output = self.fx.root / "second.json"
        self.assertEqual(self.assemble()["status"], "PLAN_ASSEMBLED")
        self.assertEqual(first, self.output.read_bytes())
        for name, raw in before.items():
            self.assertEqual((self.fx.root / name).read_bytes(), raw)

    def test_two_run_partition(self):
        other = self.fx.root / "split"
        other.mkdir()
        self.fx = SyntheticInputs(other, split=True)
        result = self.assemble()
        self.assertEqual(result["status"], "PLAN_ASSEMBLED", result)
        plan = json.loads(self.output.read_bytes())
        self.assertEqual(len(plan["runs"]), 2)

    def test_cli_persistence_digests_must_match_capture(self):
        self.fx.data[0]["report"]["results"][0]["persistence"]["beforeCatalogSha256"] = "0" * 64
        self.fx.refresh()
        self.blocked("G3_API_CAPTURE")

    def test_restored_digest_cannot_be_replaced_with_cache_digest(self):
        capture = self.fx.data[0]["api"]["controls"][self.identity]
        capture["catalogLogicalDigest"]["sha256"] = capture["cacheSha256"]
        self.fx.refresh()
        self.blocked("G3_API_CAPTURE")

    def test_report_order_does_not_change_digest_join(self):
        self.fx.data[0]["report"]["results"].reverse()
        self.fx.refresh()
        self.assertEqual(self.assemble()["status"], "PLAN_ASSEMBLED")

    def test_stale_nested_binding_even_with_matching_envelope_checks(self):
        capture = self.fx.data[0]["api"]["controls"][self.identity]
        capture["bootstrap"]["captureProvenance"]["binding"]["reportSha256"] = "0" * 64
        response_sha = sha(matrix.canonical(capture["bootstrap"]))
        self.fx.data[0]["evidence"]["controls"][self.identity]["checks"]["apiBootstrap"]["data"]["responseSha256"] = response_sha
        self.fx.data[0]["oracle"]["controls"][self.identity]["checks"]["apiBootstrap"]["responseSha256"] = response_sha
        for name in ("api", "evidence", "oracle"):
            self.fx.repin_document(name)
        self.blocked("G3_API_CAPTURE")

    def test_api_identity_alias_cannot_replace_actual_response(self):
        capture = self.fx.data[0]["api"]["controls"][self.identity]
        capture["bootstrap"]["claimedHash"] = self.identity
        capture["bootstrap"]["response"]["catalog"]["hash"] = "0" * 64
        self.fx.data[0]["oracle"]["controls"][self.identity]["bootstrap"]["romSha256"]["pointer"] = "/claimedHash"
        response_sha = sha(matrix.canonical(capture["bootstrap"]))
        self.fx.data[0]["evidence"]["controls"][self.identity]["checks"]["apiBootstrap"]["data"]["responseSha256"] = response_sha
        self.fx.data[0]["oracle"]["controls"][self.identity]["checks"]["apiBootstrap"]["responseSha256"] = response_sha
        for name in ("api", "evidence", "oracle"):
            self.fx.repin_document(name)
        self.blocked("G3_API_CAPTURE")

    def test_missing_oracle_names_g4(self):
        del self.fx.request["runs"][0]["oracle"]
        self.fx.save()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_missing_capture_names_g3(self):
        del self.fx.request["runs"][0]["api"]
        self.fx.save()
        self.blocked("G3_API_CAPTURE")

    def test_unpinned_input_and_stale_pin(self):
        for value in (None, "0" * 64):
            with self.subTest(pin=value):
                ref = self.fx.request["runs"][0]["evidence"]
                ref["sha256"] = value
                self.fx.save()
                self.blocked("G1_NORMALIZED_CHECKS")

    def test_duplicate_json_keys_and_nonfinite_numbers(self):
        for raw in (b'{"schemaVersion":1,"schemaVersion":1}', b'{"schemaVersion":NaN}'):
            with self.subTest(raw=raw):
                self.fx.request["runs"][0]["oracle"] = self.fx.write("invalid.json", raw)
                self.fx.save()
                self.blocked("G4_INDEPENDENT_ORACLE")

    def test_incomplete_and_duplicate_expected_controls(self):
        saved = copy.deepcopy(self.fx.expectations["controls"])
        for rows in (saved[:-1], saved[:-1] + [saved[0]]):
            self.fx.expectations["controls"] = rows
            self.fx.refresh()
            self.blocked("G6_EXPECTATIONS")

    def test_korean_gold_cannot_stand_for_silver(self):
        self.fx.expectations["controls"][-1]["release"] = "GOLD"
        self.fx.refresh()
        self.blocked("G6_EXPECTATIONS")

    def test_overlapping_runs_rejected(self):
        self.fx.request["runs"].append(copy.deepcopy(self.fx.request["runs"][0]))
        self.fx.save()
        self.blocked()

    def test_stale_report_receipt_and_schema(self):
        for name, key, value in (("report", "schemaVersion", 13), ("receipt", "sourceCommit", "c" * 40),
                                 ("receipt", "schemaVersion", True), ("receipt", "inputCount", 43)):
            with self.subTest(name=name, key=key):
                saved = copy.deepcopy(self.fx.data[0][name])
                self.fx.data[0][name][key] = value
                self.fx.refresh()
                self.blocked("G0_SOURCE_BOUND_RUN")
                self.fx.data[0][name] = saved

    def test_stale_bound_documents(self):
        for name in ("evidence", "api", "oracle"):
            with self.subTest(name=name):
                self.fx.refresh()
                self.fx.data[0][name]["binding"]["sourceCommit"] = "c" * 40
                self.fx.repin_document(name)
                self.blocked()

    def test_missing_check_and_skips_rejected(self):
        checks = self.fx.data[0]["evidence"]["controls"][self.identity]["checks"]
        checks["rawHeader"]["skipped"] = 1
        self.fx.refresh()
        self.blocked("G1_NORMALIZED_CHECKS")
        del checks["rawHeader"]
        self.fx.refresh()
        self.blocked("G1_NORMALIZED_CHECKS")

    def test_codec_golden_mismatch_rejected(self):
        self.fx.data[0]["oracle"]["controls"][self.identity]["checks"]["codecGoldenVectors"]["vectorSetSha256"] = "e" * 64
        self.fx.refresh()
        self.blocked("G2_CODEC_VECTORS")

    def test_canonical_bootstrap_digest_is_not_raw_document_digest(self):
        self.fx.data[0]["evidence"]["controls"][self.identity]["checks"]["apiBootstrap"]["data"]["responseSha256"] = "e" * 64
        self.fx.refresh()
        self.blocked()

    def test_api_reparse_and_independent_field_mismatch(self):
        api = self.fx.data[0]["api"]["controls"][self.identity]
        api["bootstrap"]["parserInvocations"] = 1
        self.fx.refresh()
        self.blocked()
        api["bootstrap"]["parserInvocations"] = 0
        api["fields"]["ITEM_NAMES"]["1"] = "WRONG_BUT_OBSERVED"
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_missing_capability_or_proof(self):
        caps = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]
        caps["ITEM_NAMES"]["proof"]["pointer"] = "/missing"
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")
        del caps["ITEM_NAMES"]
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_source_slice_pin_mismatch(self):
        self.fx.data[0]["proofs"]["proofs"][self.identity + "ITEM_NAMES"]["sourceSlice"]["sha256"] = "0" * 64
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_stale_proof_binding(self):
        self.fx.data[0]["proofs"]["binding"]["receiptSha256"] = "0" * 64
        proof_ref = self.fx.write("stale-proof.json", self.fx.data[0]["proofs"])
        self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["ITEM_NAMES"]["proof"]["artifact"] = proof_ref
        self.fx.repin_document("oracle")
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_conflicting_cache_pins_and_identity(self):
        self.fx.data[0]["api"]["controls"][self.identity]["cacheSha256"] = "0" * 64
        self.fx.refresh()
        self.blocked()
        self.fx.data[0]["api"]["controls"][self.identity]["cacheSha256"] = "f" * 64
        self.fx.data[0]["report"]["results"][0]["persistence"]["fileName"] = "other.sqlite"
        self.fx.refresh()
        self.blocked()

    def test_missing_independent_cache_assertion_not_filled_from_api(self):
        sample = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["ITEM_NAMES"]["records"][0]
        del sample["cache"]["value"]
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_unknown_disposition_and_unaccounted_partial(self):
        cap = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["ITEM_NAMES"]
        cap["disposition"] = "AVAILABLE_HISTORICAL"
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")
        cap.update(disposition="ACCEPTED", expectedRecords=2)
        self.fx.data[0]["report"]["results"][0]["catalog"]["localizedCapabilities"]["ITEM_NAMES"]["expectedRecords"] = 2
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_zero_count_exclusion_needs_explicit_absence(self):
        cap = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["ITEM_NAMES"]
        cap.update(disposition="EXCLUDED", coveredRecords=0, expectedRecords=0, records=[])
        self.fx.data[0]["report"]["results"][0]["catalog"]["localizedCapabilities"]["ITEM_NAMES"].update(status="NOT_APPLICABLE", coveredRecords=0, expectedRecords=0)
        proof = self.fx.data[0]["proofs"]["proofs"][self.identity + "ITEM_NAMES"]
        proof.update(kind="NOT_APPLICABLE", coveredRecords=0, expectedRecords=0, recordIds=[])
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")
        cap["absence"] = {"cache": {"pointer": "/ITEM_NAMES", "value": {}}, "api": {"pointer": "/fields/ITEM_NAMES", "value": {}}}
        self.fx.data[0]["api"]["controls"][self.identity]["fields"]["ITEM_NAMES"] = {}
        self.fx.refresh()
        result = self.assemble()
        self.assertEqual(result["status"], "PLAN_ASSEMBLED", result)
        self.assertFalse(result["acceptance"])

    def test_input_limit_and_path_sanitation(self):
        self.fx.request["runs"][0]["api"]["path"] = "PRIVATE_RELATIVE_PATH"
        self.fx.save()
        result = self.blocked()
        self.assertNotIn("PRIVATE_RELATIVE_PATH", json.dumps(result))

    def test_existing_output_never_overwritten(self):
        self.output.write_bytes(b"existing")
        result = self.assemble()
        self.assertEqual(result["status"], "BLOCKED")
        self.assertEqual(self.output.read_bytes(), b"existing")

    def test_cache_payload_paths_are_never_opened_or_statted(self):
        real_open, real_stat = Path.open, Path.stat

        def guarded_open(path, *args, **kwargs):
            self.assertNotEqual(path.suffix, ".sqlite", "cache payload opened")
            return real_open(path, *args, **kwargs)

        def guarded_stat(path, *args, **kwargs):
            self.assertNotEqual(path.suffix, ".sqlite", "cache payload statted")
            return real_stat(path, *args, **kwargs)

        with patch.object(Path, "open", guarded_open), patch.object(Path, "stat", guarded_stat):
            self.assertEqual(self.assemble()["status"], "PLAN_ASSEMBLED")

    def test_payload_source_rejected_before_open_or_stat(self):
        path = self.fx.root / "forbidden.sqlite"
        self.fx.expectations["source"] = {"path": str(path), "sha256": "0" * 64}
        self.fx.refresh()
        real_open, real_stat = Path.open, Path.stat

        def guard_open(p, *args, **kwargs):
            self.assertNotEqual(p, path)
            return real_open(p, *args, **kwargs)

        def guard_stat(p, *args, **kwargs):
            self.assertNotEqual(p, path)
            return real_stat(p, *args, **kwargs)

        with patch.object(Path, "open", guard_open), patch.object(Path, "stat", guard_stat):
            self.blocked("G6_EXPECTATIONS")

    def test_output_close_failure_removes_owned_partial_plan(self):
        real_open = Path.open

        class FailClose:
            def __init__(self, handle):
                self.handle = handle

            def __enter__(self):
                return self.handle

            def __exit__(self, *args):
                self.handle.close()
                raise OSError("PRIVATE_OUTPUT_ERROR")

        def fail_close(path, *args, **kwargs):
            handle = real_open(path, *args, **kwargs)
            return FailClose(handle) if path == self.output else handle

        with patch.object(Path, "open", fail_close):
            result = self.blocked("G6_OUTPUT")
        self.assertNotIn("PRIVATE_OUTPUT_ERROR", json.dumps(result))

    def test_same_path_conflicting_pin_and_observation_alias(self):
        original = self.fx.request["runs"][0]["oracle"]
        self.fx.request["runs"][0]["oracle"] = dict(self.fx.request["runs"][0]["api"], sha256=original["sha256"])
        self.fx.save()
        result = self.blocked()
        self.assertEqual(result["blockers"][0]["code"], "CONFLICTING_PIN")
        self.fx.request["runs"][0]["oracle"] = dict(self.fx.request["runs"][0]["api"])
        self.fx.save()
        self.blocked()

    def test_request_and_source_pins_must_match_bytes(self):
        correct = self.fx.pin["sha256"]
        self.fx.pin["sha256"] = "0" * 64
        self.blocked("G6_ASSEMBLY_REQUEST")
        self.fx.pin["sha256"] = correct
        Path(self.fx.expectations["source"]["path"]).write_bytes(b"changed independent source")
        self.blocked("G6_EXPECTATIONS")

    def test_duplicate_manifest_report_and_missing_api_id(self):
        original = copy.deepcopy(self.fx.data[0])
        for name in ("manifest", "report", "api"):
            with self.subTest(name=name):
                self.fx.data[0] = copy.deepcopy(original)
                if name == "manifest":
                    self.fx.data[0][name][1] = copy.deepcopy(self.fx.data[0][name][0])
                elif name == "report":
                    self.fx.data[0][name]["results"][1] = copy.deepcopy(self.fx.data[0][name]["results"][0])
                else:
                    del self.fx.data[0][name]["controls"][self.identity]
                self.fx.refresh()
                self.blocked()

    def test_schema_sections_and_unexpected_request_fields(self):
        original = copy.deepcopy(self.fx.expectations)
        for key, value in (("schemaVersion", True), ("parserSchemaVersion", True),
                           ("requiredSections", list(matrix.SECTIONS)[:-1])):
            self.fx.expectations = copy.deepcopy(original)
            self.fx.expectations[key] = value
            self.fx.refresh()
            self.blocked("G6_EXPECTATIONS")
        self.fx.expectations = original
        self.fx.refresh()
        self.fx.request["acceptance"] = True
        self.fx.save()
        self.blocked("G6_ASSEMBLY_REQUEST")

    def test_bounded_metadata_and_aggregate_reads(self):
        self.fx.request["runs"][0]["api"] = {"path": str(self.fx.root / "oversized.json"), "sha256": "0" * 64}
        with (self.fx.root / "oversized.json").open("wb") as handle:
            handle.truncate(matrix.MAX_JSON_BYTES + 1)
        self.fx.save()
        self.blocked("G3_API_CAPTURE")
        self.fx.refresh()
        with patch.object(matrix, "MAX_AGGREGATE_BYTES", 1024):
            result = self.blocked()
        self.assertEqual(result["blockers"][0]["code"], "INPUT_LIMIT")

    def test_supported_reserved_slot_proof_and_core_exclusion_refusal(self):
        cap = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["ITEM_NAMES"]
        cap["expectedRecords"] = 2
        self.fx.data[0]["report"]["results"][0]["catalog"]["localizedCapabilities"]["ITEM_NAMES"].update(status="PARTIAL", expectedRecords=2)
        proofs = self.fx.data[0]["proofs"]["proofs"]
        key = self.identity + "ITEM_NAMES"
        proofs[key]["expectedRecords"] = 2
        proofs[key + "-excluded"] = dict(proofs[key], kind="RESERVED_SLOT", recordIds=["2"])
        cap["excluded"] = [{"id": "2", "proof": {"pointer": "/proofs/" + key + "-excluded"}}]
        self.fx.refresh()
        self.assertEqual(self.assemble()["status"], "PLAN_ASSEMBLED")
        self.output.unlink()
        core = self.fx.data[0]["oracle"]["controls"][self.identity]["capabilities"]["SPECIES_NAMES"]
        core.update(disposition="EXCLUDED", coveredRecords=0, expectedRecords=0, records=[])
        self.fx.data[0]["report"]["results"][0]["catalog"]["localizedCapabilities"]["SPECIES_NAMES"].update(status="NOT_APPLICABLE", coveredRecords=0, expectedRecords=0)
        self.fx.refresh()
        self.blocked("G4_INDEPENDENT_ORACLE")

    def test_cli_success_failure_and_help(self):
        command = [sys.executable, "-B", str(MODULE), "--request", self.fx.pin["path"],
                   "--request-sha256", self.fx.pin["sha256"], "--source-commit", COMMIT, "--output", str(self.output)]
        p = subprocess.run(command, capture_output=True, text=True, timeout=20)
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        self.assertEqual(json.loads(p.stdout)["status"], "PLAN_ASSEMBLED")
        self.assertNotIn(self.temp.name, p.stdout + p.stderr)
        p = subprocess.run(command + ["--unknown", "PRIVATE_VALUE"], capture_output=True, text=True, timeout=20)
        self.assertEqual(p.returncode, 1)
        self.assertNotIn("PRIVATE_VALUE", p.stdout + p.stderr)
        p = subprocess.run([sys.executable, "-B", str(MODULE), "--help"], capture_output=True, text=True, timeout=20)
        self.assertEqual(p.returncode, 0)
        self.assertIn("not acceptance", p.stdout)


class G3BindingTests(unittest.TestCase):
    """Pure metadata boundary tests; no files or cache payloads are needed."""
    def setUp(self):
        self.control = {"sha256": "1" * 64, "family": "EMERALD", "language": "fr",
                        "codecId": "fabricated-fr", "codecVersion": 1}
        self.binding = {"sourceCommit": COMMIT, "sourceSha256": sha(SOURCE), "reportSha256": "2" * 64,
                        "receiptSha256": "3" * 64, "generatorSha256": GENERATOR}
        self.plan = {"reportSchemaVersion": 16, "parserSchemaVersion": 64, "cacheSchemaVersion": 2}
        self.row = {"persistence": {"logicalDigestVersion": 1, "beforeCatalogSha256": logical_digest(self.control),
                                    "afterCatalogSha256": logical_digest(self.control)}}
        self.capture = {"acceptance": False, "scope": "CACHE_ONLY_OBSERVATION", "cacheSha256": "f" * 64,
                        "catalogLogicalDigest": {"version": 1, "sha256": logical_digest(self.control)},
                        "bootstrap": fixture_bootstrap(self.control, "f" * 64, copy.deepcopy(self.binding))}
        self.observed = {"cacheSha256": "f" * 64,
                         "checks": {k: {"data": v} for k, v in declared_checks(self.control).items()}}
        self.expected = {"bootstrap": {
            "romSha256": {"pointer": "/response/catalog/hash", "value": "1" * 64},
            "language": {"pointer": "/response/language/activeLanguage", "value": "fr"},
            "authority": {"pointer": "/response/language/authority", "value": "ROM_DEFAULT"},
            "parserInvocations": {"pointer": "/parserInvocations", "value": 0}}}

    def check(self):
        matrix.g3_capture(self.capture, self.row, self.observed, self.expected, self.control, self.plan, self.binding)

    def test_valid_metadata_is_not_rewritten(self):
        keys = ("capture", "row", "observed", "expected", "control", "plan", "binding")
        before = {key: copy.deepcopy(getattr(self, key)) for key in keys}
        self.check()
        for key in keys:
            self.assertEqual(getattr(self, key), before[key])

    def test_missing_or_unsupported_logical_versions(self):
        for target, key in ((self.row["persistence"], "logicalDigestVersion"),
                            (self.capture["catalogLogicalDigest"], "version")):
            for value in (None, True, False, 0, 2, "1", 1.0):
                with self.subTest(key=key, value=value):
                    target[key] = value
                    with self.assertRaises(matrix.Blocked):
                        self.check()
            target[key] = 1
            del target[key]
            with self.assertRaises(matrix.Blocked):
                self.check()
            target[key] = 1

    def test_every_logical_digest_must_agree(self):
        for target, key in ((self.row["persistence"], "beforeCatalogSha256"),
                            (self.row["persistence"], "afterCatalogSha256"),
                            (self.capture["catalogLogicalDigest"], "sha256"),
                            (self.observed["checks"]["reopenParity"]["data"], "beforeCatalogSha256"),
                            (self.observed["checks"]["reopenParity"]["data"], "afterCatalogSha256")):
            for value in (None, "e" * 64, "A" * 64, "", True):
                with self.subTest(key=key, value=value):
                    target[key] = value
                    with self.assertRaises(matrix.Blocked):
                        self.check()
            target[key] = logical_digest(self.control)

    def test_each_embedded_binding_field_is_required_and_exact(self):
        provenance = self.capture["bootstrap"]["captureProvenance"]
        for key in self.binding:
            with self.subTest(key=key):
                provenance["binding"][key] = "0" * len(self.binding[key])
                with self.assertRaises(matrix.Blocked):
                    self.check()
                del provenance["binding"][key]
                with self.assertRaises(matrix.Blocked):
                    self.check()
                provenance["binding"] = copy.deepcopy(self.binding)
        provenance["binding"]["extra"] = "not the exact run binding"
        with self.assertRaises(matrix.Blocked):
            self.check()

    def test_provenance_schema_and_restore_method_are_not_optional(self):
        provenance = self.capture["bootstrap"]["captureProvenance"]
        saved = copy.deepcopy(provenance)
        cases = [("method", "PARSE"), ("sourceCacheSha256", "0" * 64), ("knowledgeMode", "OMNISCIENT")]
        cases += [(key, value) for key in ("parserSchemaVersion", "sqlSchemaVersion", "catalogLogicalDigestVersion")
                  for value in (None, True, "1", 0, 99)]
        for key, value in cases:
            with self.subTest(key=key, value=value):
                provenance[key] = value
                with self.assertRaises(matrix.Blocked):
                    self.check()
                provenance[key] = saved[key]

    def test_actual_response_and_measurement_paths_are_required(self):
        cases = [("/acceptance", 0), ("/scope", "ACCEPTED"), ("/bootstrap/parserInvocations", False),
                 ("/bootstrap/parserInvocations", 1), ("/bootstrap/response/catalog/hash", "0" * 64),
                 ("/bootstrap/response/catalog/family", "CRYSTAL"),
                 ("/bootstrap/response/language/manifestStatus", "UNKNOWN"),
                 ("/bootstrap/response/language/defaultLanguage", "en"),
                 ("/bootstrap/response/language/activeLanguage", "en"),
                 ("/bootstrap/response/language/authority", "LIVE_RAM"),
                 ("/bootstrap/response/state/loading/phase", "PARSE"),
                 ("/bootstrap/response/state/settings/knowledgeMode", "OMNISCIENT"),
                 ("/bootstrap/response/language/projections/0/codecVersion", True),
                 ("/bootstrap/response/language/projections/0/codecId", "other"),
                 ("/bootstrap/response/language/projections", []),
                 ("/bootstrap/response/language/projections", [None])]
        for path, value in cases:
            with self.subTest(path=path, value=value):
                parent, key = path.rsplit("/", 1)
                target = matrix.pointer(self.capture, parent) if parent else self.capture
                saved = target[key]
                target[key] = value
                with self.assertRaises(matrix.Blocked):
                    self.check()
                target[key] = saved
        del self.capture["bootstrap"]["response"]["state"]["settings"]
        with self.assertRaises(matrix.Blocked):
            self.check()

    def test_oracle_pointer_alias_is_rejected_even_if_value_agrees(self):
        self.expected["bootstrap"]["language"]["pointer"] = "/response/language/defaultLanguage"
        with self.assertRaises(matrix.Blocked):
            self.check()

    def test_pre_digest_report_formats_remain_ineligible(self):
        for version in (14, 15, True, "16", 17):
            with self.subTest(version=version):
                self.plan["reportSchemaVersion"] = version
                with self.assertRaises(matrix.Blocked):
                    self.check()


if __name__ == "__main__":
    unittest.main(verbosity=2)
