import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_official_matrix_observations as subject
import official_matrix as matrix


class Fixture:
    def __init__(self, root):
        self.root = Path(root)
        self.source_commit = "a" * 40
        self.identity = "b" * 64
        self.generator = "c" * 64
        self.source = self.write("source.json", {"authority": "independent"})
        self.codec = self.write("codec.json", {
            "schemaVersion": 1,
            "status": "EXECUTED",
            "acceptance": False,
            "sourceBinding": {"gitHead": self.source_commit},
            "codecs": [{
                "identity": {"codecId": "gb-gen1-en", "codecVersion": 1, "language": "en"},
                "check": {
                    "status": "PASS", "tests": 2, "failures": 0, "errors": 0, "skipped": 0,
                    "data": {"vectorSetSha256": "d" * 64, "vectorCount": 2, "matchedCount": 2},
                },
            }],
        })
        logical = "e" * 64
        self.report = self.write("report.json", {
            "schemaVersion": 16,
            "execution": {"sourceCommit": self.source_commit, "generatorSha256": self.generator},
            "results": [{
                "result": {
                    "sha256": self.identity, "status": "SELECTED", "selectedFamily": "RED_BLUE",
                    "probes": [{"family": "RED_BLUE", "resolvedLayout": {
                        "family": "RED_BLUE", "generation": 1, "platform": "GB", "speciesCount": 151,
                        "moveCount": 165, "tables": {"speciesNames": {"offset": 1}, "moveNames": {"offset": 2},
                        "baseStats": None}, "resolvedDatasets": {}, "languageManifest": {"status": "RESOLVED"},
                    }}],
                },
                "rawHeader": {"rawHeaderSha256": "f" * 64, "byteCount": 80},
                "persistence": {"logicalDigestVersion": 1, "beforeCatalogSha256": logical,
                    "afterCatalogSha256": logical},
            }],
        })
        self.receipt = self.write("receipt.json", {
            "schemaVersion": 1, "sourceCommit": self.source_commit,
            "generator": {"name": "parser-cli", "schemaVersion": 16, "sha256": self.generator},
            "rawReportSha256": self.sha(self.report), "inputCount": 1,
        })
        binding = self.binding()
        bootstrap = {"response": {"catalog": {"hash": self.identity}, "language": {
            "activeLanguage": "en", "projections": [{
                "language": "en", "codecId": "gb-gen1-en", "codecVersion": 1,
            }],
        }}, "parserInvocations": 0,
            "captureProvenance": {"binding": binding}}
        self.api = self.write("api.json", {
            "schemaVersion": 1, "binding": binding, "controls": {self.identity: {
                "acceptance": False, "scope": "CACHE_ONLY_OBSERVATION", "cacheSha256": "0" * 64,
                "catalogLogicalDigest": {"version": 1, "sha256": logical},
                "fields": {},
                "measurements": {
                    "projectionIsolation": {"fieldsChecked": 15, "mixedFields": 0,
                        "fallbackFields": 0, "sharedTextFields": 0},
                    "typeSemantics": {"typesChecked": 15, "unresolvedTypes": 0, "mismatchedTypes": 0},
                },
                "bootstrap": bootstrap,
            }},
        })

    def write(self, name, value):
        path = self.root / name
        path.write_bytes(matrix.canonical(value) + b"\n")
        return path

    @staticmethod
    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def binding(self):
        return {"sourceCommit": self.source_commit, "sourceSha256": self.sha(self.source),
            "reportSha256": self.sha(self.report), "receiptSha256": self.sha(self.receipt),
            "generatorSha256": self.generator}

    def refs(self):
        return {name: {"path": str(getattr(self, name)), "sha256": self.sha(getattr(self, name))}
            for name in ("source", "report", "receipt", "api", "codec")}


class OfficialMatrixObservationsTest(unittest.TestCase):
    def test_builds_all_seven_checks_from_current_outputs(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = Fixture(root)
            result = subject.build(fixture.refs(), fixture.source_commit)
            self.assertEqual(1, result["schemaVersion"])
            self.assertEqual(fixture.binding(), result["binding"])
            control = result["controls"][fixture.identity]
            self.assertEqual("f" * 64, control["checks"]["rawHeader"]["data"]["rawHeaderSha256"])
            self.assertEqual(2, control["checks"]["codecGoldenVectors"]["tests"])
            self.assertEqual(2, control["checks"]["structuralAuthority"]["data"]["corroboratingTableCount"])
            self.assertEqual("e" * 64, control["checks"]["reopenParity"]["data"]["afterCatalogSha256"])
            self.assertEqual(15, control["checks"]["projectionIsolation"]["tests"])
            self.assertEqual(15, control["checks"]["typeSemantics"]["tests"])
            expected = hashlib.sha256(matrix.canonical(fixture.api and json.loads(fixture.api.read_text())["controls"][fixture.identity]["bootstrap"])).hexdigest()
            self.assertEqual(expected, control["checks"]["apiBootstrap"]["data"]["responseSha256"])

    def test_cli_creates_one_pinned_output(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = Fixture(root)
            output = Path(root) / "evidence.json"
            arguments = []
            for name, ref in fixture.refs().items():
                arguments.extend(("--" + name, ref["path"], "--" + name + "-sha256", ref["sha256"]))
            arguments.extend(("--source-commit", fixture.source_commit, "--output", str(output)))
            self.assertEqual(0, subject.main(arguments))
            self.assertTrue(output.is_file())
            self.assertEqual({fixture.identity}, set(json.loads(output.read_text())["controls"]))
            self.assertEqual(1, subject.main(arguments))

    def test_rejects_failed_actual_measurement(self):
        with tempfile.TemporaryDirectory() as root:
            fixture = Fixture(root)
            api = json.loads(fixture.api.read_text())
            api["controls"][fixture.identity]["measurements"]["projectionIsolation"]["mixedFields"] = 1
            fixture.api.write_bytes(matrix.canonical(api) + b"\n")
            with self.assertRaises(matrix.Blocked):
                subject.build(fixture.refs(), fixture.source_commit)


if __name__ == "__main__":
    unittest.main()
