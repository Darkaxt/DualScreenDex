"""Build bound G1 observations from a parser-cli run and its G3 cache capture.

This exporter reads no ROM or cache payload, creates no oracle values, and makes no
semantic-acceptance claim. It only normalizes current executed observations.
"""
import argparse
from pathlib import Path
import sys

import official_matrix as matrix


STRUCTURAL_KEYS = (
    "family", "generation", "platform", "speciesCount", "moveCount", "tables",
    "pokeemeraldExpansion", "headerlessUnifiedSpecies", "expandedSplitCaptureBalls",
    "compiledGbaReferences", "learnsetTables", "learnsetSelector", "resolvedDatasets",
    "languageManifest", "itemRootNomination", "itemNameAuthority",
)


def exact_check(data, tests):
    matrix.require(matrix.integer(tests, 1), "REQUIRED_CHECK")
    return {
        "status": "PASS", "tests": tests, "failures": 0, "errors": 0,
        "skipped": 0, "data": data,
    }


def codec_checks(document, source_commit):
    matrix.require(
        isinstance(document, dict) and document.get("schemaVersion") == 1 and
        document.get("status") == "EXECUTED" and document.get("acceptance") is False,
        "REQUIRED_CHECK",
    )
    source = document.get("sourceBinding")
    matrix.require(isinstance(source, dict) and source.get("gitHead") == source_commit, "PROVENANCE")
    rows = document.get("codecs")
    matrix.require(isinstance(rows, list) and 1 <= len(rows) <= 64, "REQUIRED_CHECK")
    result = {}
    for row in rows:
        identity, check = row.get("identity"), row.get("check")
        matrix.require(isinstance(identity, dict) and isinstance(check, dict), "REQUIRED_CHECK")
        key = (identity.get("codecId"), identity.get("codecVersion"), identity.get("language"))
        matrix.require(
            isinstance(key[0], str) and matrix.integer(key[1], 1) and isinstance(key[2], str) and
            key not in result and check.get("status") == "PASS" and
            all(check.get(name) == 0 for name in ("failures", "errors", "skipped")),
            "REQUIRED_CHECK",
        )
        data = check.get("data")
        matrix.require(
            isinstance(data, dict) and matrix.hash_value(data.get("vectorSetSha256")) and
            matrix.integer(data.get("vectorCount"), 1) and
            data.get("matchedCount") == data.get("vectorCount") and
            check.get("tests") == data.get("vectorCount"),
            "REQUIRED_CHECK",
        )
        result[key] = exact_check(data, check["tests"])
    return result


def selected_layout(row, identity):
    result = row.get("result")
    matrix.require(
        isinstance(result, dict) and result.get("sha256") == identity and
        result.get("status") == "SELECTED" and isinstance(result.get("selectedFamily"), str),
        "LANGUAGE_AUTHORITY",
    )
    probes = [probe for probe in result.get("probes", []) if probe.get("family") == result["selectedFamily"]]
    matrix.require(len(probes) == 1 and isinstance(probes[0].get("resolvedLayout"), dict), "LANGUAGE_AUTHORITY")
    return probes[0]["resolvedLayout"]


def structural_check(layout):
    tables = layout.get("tables")
    matrix.require(isinstance(tables, dict), "REQUIRED_CHECK")
    table_count = sum(value is not None for value in tables.values())
    matrix.require(table_count >= 2, "REQUIRED_CHECK")
    projection = {key: layout.get(key) for key in STRUCTURAL_KEYS}
    return exact_check({
        "consumerEvidenceSha256": matrix.digest(matrix.canonical(projection)),
        "corroboratingTableCount": table_count,
    }, table_count)


def measurement_check(value, count_key, zero_keys):
    matrix.require(isinstance(value, dict) and matrix.integer(value.get(count_key), 1), "REQUIRED_CHECK")
    matrix.require(all(value.get(key) == 0 for key in zero_keys), "REQUIRED_CHECK")
    return exact_check(value, value[count_key])


def build(refs, source_commit):
    matrix.require(matrix.hash_value(source_commit, 40), "PROVENANCE")
    matrix.require(isinstance(refs, dict) and set(refs) == {"source", "report", "receipt", "api", "codec"},
                   "INPUT_SCHEMA")
    inputs = matrix.Inputs()
    source = inputs.raw(refs["source"])
    matrix.require(bool(source), "INPUT_MISSING")
    report = inputs.document(refs["report"], matrix.MAX_REPORT_BYTES)
    receipt = inputs.document(refs["receipt"])
    api = inputs.document(refs["api"], matrix.MAX_API_BYTES)
    codecs = codec_checks(inputs.document(refs["codec"]), source_commit)
    generator = report.get("execution", {}).get("generatorSha256")
    binding = {
        "sourceCommit": source_commit,
        "sourceSha256": refs["source"]["sha256"],
        "reportSha256": refs["report"]["sha256"],
        "receiptSha256": refs["receipt"]["sha256"],
        "generatorSha256": generator,
    }
    matrix.require(
        report.get("schemaVersion") == 16 and report.get("execution") == {
            "sourceCommit": source_commit, "generatorSha256": generator,
        } and matrix.hash_value(generator),
        "PROVENANCE",
    )
    matrix.require(
        receipt.get("schemaVersion") == 1 and receipt.get("sourceCommit") == source_commit and
        receipt.get("generator") == {"name": "parser-cli", "schemaVersion": 16, "sha256": generator} and
        receipt.get("rawReportSha256") == refs["report"]["sha256"],
        "PROVENANCE",
    )
    matrix.bound_document(api, binding)
    rows = matrix.identities(report.get("results"), lambda row: row["result"]["sha256"])
    captures = api.get("controls")
    matrix.require(
        isinstance(captures, dict) and set(captures) == set(rows) and
        receipt.get("inputCount") == len(rows),
        "CONTROL_IDENTITY",
    )
    controls = {}
    for identity, row in rows.items():
        layout = selected_layout(row, identity)
        capture = captures[identity]
        matrix.require(
            isinstance(capture, dict) and capture.get("acceptance") is False and
            capture.get("scope") == "CACHE_ONLY_OBSERVATION" and
            matrix.hash_value(capture.get("cacheSha256")),
            "API_ACCEPTANCE",
        )
        header = row.get("rawHeader")
        matrix.require(
            isinstance(header, dict) and matrix.hash_value(header.get("rawHeaderSha256")) and
            matrix.integer(header.get("byteCount"), 1, 512),
            "REQUIRED_CHECK",
        )
        persistence = row.get("persistence")
        logical = capture.get("catalogLogicalDigest")
        matrix.require(
            isinstance(persistence, dict) and isinstance(logical, dict) and logical.get("version") == 1 and
            matrix.hash_value(logical.get("sha256")) and persistence.get("logicalDigestVersion") == 1 and
            persistence.get("beforeCatalogSha256") == logical["sha256"] and
            persistence.get("afterCatalogSha256") == logical["sha256"],
            "CATALOG_LOGICAL_DIGEST",
        )
        measurements = capture.get("measurements")
        matrix.require(isinstance(measurements, dict), "REQUIRED_CHECK")
        projection = measurement_check(
            measurements.get("projectionIsolation"), "fieldsChecked",
            ("mixedFields", "fallbackFields", "sharedTextFields"),
        )
        types = measurement_check(
            measurements.get("typeSemantics"), "typesChecked", ("unresolvedTypes", "mismatchedTypes"),
        )
        envelope = capture.get("bootstrap")
        matrix.require(
            isinstance(envelope, dict) and envelope.get("parserInvocations") == 0 and
            envelope.get("captureProvenance", {}).get("binding") == binding,
            "API_ACCEPTANCE",
        )
        language = envelope.get("response", {}).get("language", {}).get("activeLanguage")
        projection_rows = envelope.get("response", {}).get("language", {}).get("projections", [])
        matrix.require(isinstance(language, str) and len(projection_rows) == 1, "LANGUAGE_AUTHORITY")
        codec_key = (projection_rows[0].get("codecId"), projection_rows[0].get("codecVersion"), language)
        matrix.require(codec_key in codecs, "REQUIRED_CHECK")
        controls[identity] = {
            "cacheSha256": capture["cacheSha256"],
            "checks": {
                "rawHeader": exact_check(header, 1),
                "codecGoldenVectors": codecs[codec_key],
                "structuralAuthority": structural_check(layout),
                "reopenParity": exact_check({
                    "beforeCatalogSha256": logical["sha256"],
                    "afterCatalogSha256": logical["sha256"],
                }, 1),
                "projectionIsolation": projection,
                "typeSemantics": types,
                "apiBootstrap": exact_check({
                    "responseSha256": matrix.digest(matrix.canonical(envelope)),
                    "parserInvocations": 0,
                }, 1),
            },
        }
    return {"schemaVersion": 1, "binding": binding, "controls": controls}


def main(argv=None):
    parser = matrix.Arguments(description=__doc__, allow_abbrev=False)
    for name in ("source", "report", "receipt", "api", "codec"):
        parser.add_argument("--" + name, required=True)
        parser.add_argument("--" + name + "-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True)
    try:
        args = parser.parse_args(argv)
        output = Path(args.output)
        matrix.require(output.is_absolute() and output.parent.is_dir() and not output.exists(), "OUTPUT_PATH")
        refs = {
            name: {"path": getattr(args, name), "sha256": getattr(args, name + "_sha256")}
            for name in ("source", "report", "receipt", "api", "codec")
        }
        evidence = build(refs, args.source_commit)
        raw = matrix.canonical(evidence) + b"\n"
        matrix.require(len(raw) <= matrix.MAX_JSON_BYTES, "INPUT_LIMIT")
        created = False
        try:
            with output.open("xb") as handle:
                created = True
                matrix.require(handle.write(raw) == len(raw), "OUTPUT_IO")
        except (OSError, matrix.Blocked):
            if created:
                output.unlink(missing_ok=True)
            raise
        result = {"schemaVersion": 1, "status": "OBSERVATIONS_EXPORTED", "acceptance": False,
                  "controls": len(evidence["controls"]), "sha256": matrix.digest(raw)}
    except matrix.Blocked as error:
        result = {"schemaVersion": 1, "status": "BLOCKED", "acceptance": False,
                  "blockers": [{"code": error.args[0]}]}
    except OSError:
        result = {"schemaVersion": 1, "status": "BLOCKED", "acceptance": False,
                  "blockers": [{"code": "INPUT_IO"}]}
    except (KeyError, TypeError, ValueError, AttributeError, IndexError, RecursionError, OverflowError):
        result = {"schemaVersion": 1, "status": "BLOCKED", "acceptance": False,
                  "blockers": [{"code": "INPUT_SCHEMA"}]}
    print(matrix.canonical(result).decode("utf-8"))
    return 0 if result["status"] == "OBSERVATIONS_EXPORTED" else 1


if __name__ == "__main__":
    sys.exit(main())
