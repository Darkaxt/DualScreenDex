"""Build the independent pre-capture official capability applicability audit.

The input is metadata only. ROM paths and all other manifest fields are ignored.
The output states the final disposition each capability must earn; it never
claims that current parser output has already earned acceptance.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


CAPABILITIES = tuple(
    "SPECIES_NAMES SPECIES_DESCRIPTIONS MOVE_NAMES MOVE_DESCRIPTIONS "
    "ABILITY_NAMES ABILITY_DESCRIPTIONS TYPE_NAMES NATURE_NAMES ITEM_NAMES "
    "AREA_NAMES LOCAL_MAP_NAMES WORLD_REGION_NAMES WORLD_LOCATION_NAMES "
    "ENCOUNTER_AREA_NAMES POI_TEXT".split()
)
FAMILIES = (
    "RED_BLUE",
    "YELLOW",
    "GOLD_SILVER",
    "CRYSTAL",
    "RUBY_SAPPHIRE",
    "EMERALD",
    "FIRERED_LEAFGREEN",
)
WESTERN_LANGUAGES = ("en", "fr", "de", "it", "es")
HASH = re.compile(r"[0-9a-f]{64}\Z")
COMMIT = re.compile(r"[0-9a-f]{40}\Z")
BASE_RECORD_EXCLUSIONS = ["RESERVED_SLOT", "NOT_APPLICABLE"]
POI_RECORD_EXCLUSIONS = BASE_RECORD_EXCLUSIONS + [
    "CONTEXTUAL_TEXT",
    "NO_TEXT",
    "UNRESOLVED",
]

GENERATION = {
    "RED_BLUE": 1,
    "YELLOW": 1,
    "GOLD_SILVER": 2,
    "CRYSTAL": 2,
    "RUBY_SAPPHIRE": 3,
    "EMERALD": 3,
    "FIRERED_LEAFGREEN": 3,
}

EXCLUSIONS = {
    1: {
        "MOVE_DESCRIPTIONS": "GEN1_HAS_NO_MOVE_DESCRIPTION_DOMAIN",
        "ABILITY_NAMES": "PRE_ABILITY_GENERATION",
        "ABILITY_DESCRIPTIONS": "PRE_ABILITY_GENERATION",
        "NATURE_NAMES": "PRE_NATURE_GENERATION",
        "AREA_NAMES": "NO_DISTINCT_RUNTIME_AREA_NAME_DOMAIN",
        "WORLD_REGION_NAMES": "GRAPHICS_ONLY_WORLD_RASTER",
    },
    2: {
        "ABILITY_NAMES": "PRE_ABILITY_GENERATION",
        "ABILITY_DESCRIPTIONS": "PRE_ABILITY_GENERATION",
        "NATURE_NAMES": "PRE_NATURE_GENERATION",
        "AREA_NAMES": "NO_DISTINCT_RUNTIME_AREA_NAME_DOMAIN",
        "WORLD_REGION_NAMES": "GRAPHICS_ONLY_WORLD_RASTER",
    },
    3: {},
}
GEN3_FAMILY_EXCLUSIONS = {
    "FIRERED_LEAFGREEN": {
        "AREA_NAMES": "NO_DISTINCT_RUNTIME_AREA_NAME_DOMAIN",
        "WORLD_REGION_NAMES": "GRAPHICS_ONLY_WORLD_RASTER",
    },
}


def fail(code):
    raise ValueError(code)


def normalized_control(value):
    if not isinstance(value, dict):
        fail("CONTROL_SCHEMA")
    identity = value.get("sha256")
    family = value.get("family")
    language = value.get("language")
    release = value.get("release")
    if not isinstance(identity, str) or not HASH.fullmatch(identity):
        fail("CONTROL_IDENTITY")
    if family not in FAMILIES or language not in {*WESTERN_LANGUAGES, "ja", "ko"}:
        fail("CONTROL_ROSTER")
    if not isinstance(release, str) or not release or len(release) > 64:
        fail("CONTROL_ROSTER")
    return {
        "sha256": identity,
        "family": family,
        "language": language,
        "release": release,
    }


def validate_roster(controls):
    if not isinstance(controls, list) or len(controls) != 44:
        fail("CONTROL_ROSTER")
    identities = [control["sha256"] for control in controls]
    if len(set(identities)) != 44:
        fail("CONTROL_IDENTITY")
    cells = Counter((control["language"], control["family"]) for control in controls)
    expected = Counter(
        (language, family)
        for language in (*WESTERN_LANGUAGES, "ja")
        for family in FAMILIES
    )
    expected[("ko", "GOLD_SILVER")] = 2
    if cells != expected:
        fail("CONTROL_ROSTER")
    korean_releases = {
        control["release"]
        for control in controls
        if control["language"] == "ko"
    }
    if korean_releases != {"GOLD", "SILVER"}:
        fail("CONTROL_ROSTER")
    return len(cells)


def capability_policy(family):
    exclusions = dict(EXCLUSIONS[GENERATION[family]])
    exclusions.update(GEN3_FAMILY_EXCLUSIONS.get(family, {}))
    result = {}
    for capability in CAPABILITIES:
        reason = exclusions.get(capability)
        result[capability] = {
            "requiredFinalDisposition": "EXCLUDED" if reason else "ACCEPTED",
            "reason": reason or (
                "RETAINED_POI_DOMAIN_REQUIRES_TYPED_COVERAGE"
                if capability == "POI_TEXT"
                else "ROM_NATIVE_TEXT_DOMAIN_REQUIRED"
            ),
            "capabilityProofKind": "NOT_APPLICABLE" if reason else "FIELD_ACCEPTANCE",
            "recordExclusionProofKinds": (
                POI_RECORD_EXCLUSIONS if capability == "POI_TEXT"
                else BASE_RECORD_EXCLUSIONS
            ),
        }
    return result


def build_audit(raw_controls, source_commit):
    if not isinstance(source_commit, str) or not COMMIT.fullmatch(source_commit):
        fail("SOURCE_COMMIT")
    controls = [normalized_control(control) for control in raw_controls]
    cells = validate_roster(controls)
    audited = []
    disposition_counts = Counter()
    for control in sorted(
        controls,
        key=lambda value: (
            (*WESTERN_LANGUAGES, "ja", "ko").index(value["language"]),
            FAMILIES.index(value["family"]),
            value["release"],
        ),
    ):
        capabilities = capability_policy(control["family"])
        disposition_counts.update(
            value["requiredFinalDisposition"] for value in capabilities.values()
        )
        audited.append({
            **control,
            "generation": GENERATION[control["family"]],
            "capabilities": capabilities,
        })
    total = len(audited) * len(CAPABILITIES)
    if (total, disposition_counts["ACCEPTED"], disposition_counts["EXCLUDED"]) != (660, 506, 154):
        fail("CAPABILITY_TOTALS")
    return {
        "schemaVersion": 1,
        "artifactKind": "OFFICIAL_CAPABILITY_APPLICABILITY_AUDIT",
        "scope": "PRE_CAPTURE_APPLICABILITY_ORACLE",
        "acceptance": False,
        "sourceCommit": source_commit,
        "authority": {
            "denominators": "CatalogLocalizedTextExtractor expectedRecords contract",
            "regions": "explicit static-name-required versus graphics-only world-region contract",
            "poi": "every retained POI remains in the POI_TEXT denominator",
            "boundary": "ROM-native content only; application/interface copy is excluded",
        },
        "summary": {
            "controls": len(audited),
            "cells": cells,
            "capabilitiesPerControl": len(CAPABILITIES),
            "capabilityDispositions": total,
            "requiredAccepted": disposition_counts["ACCEPTED"],
            "requiredExcluded": disposition_counts["EXCLUDED"],
            "finalAcceptedControls": 0,
        },
        "controls": audited,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        fail("OUTPUT_EXISTS")
    document = json.loads(args.inventory.read_text(encoding="utf-8"))
    controls = document.get("controls") if isinstance(document, dict) else document
    result = build_audit(controls, args.source_commit)
    args.output.write_text(
        json.dumps(result, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result["summary"], sort_keys=True))


if __name__ == "__main__":
    main()
