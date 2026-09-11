import unittest

import build_official_capability_audit as audit


FAMILIES = (
    "RED_BLUE",
    "YELLOW",
    "GOLD_SILVER",
    "CRYSTAL",
    "RUBY_SAPPHIRE",
    "EMERALD",
    "FIRERED_LEAFGREEN",
)


class CapabilityAuditTests(unittest.TestCase):
    def controls(self):
        controls = []
        index = 1
        for language in ("en", "fr", "de", "it", "es", "ja"):
            for family in FAMILIES:
                controls.append({
                    "sha256": f"{index:064x}",
                    "family": family,
                    "language": language,
                    "release": f"{family}_CONTROL",
                    "privatePath": f"private/{language}/{family}.rom",
                })
                index += 1
        controls.extend((
            {
                "sha256": f"{index:064x}",
                "family": "GOLD_SILVER",
                "language": "ko",
                "release": "GOLD",
                "privatePath": "private/ko/gold.gbc",
            },
            {
                "sha256": f"{index + 1:064x}",
                "family": "GOLD_SILVER",
                "language": "ko",
                "release": "SILVER",
                "privatePath": "private/ko/silver.gbc",
            },
        ))
        return controls

    def build(self, controls=None):
        return audit.build_audit(
            self.controls() if controls is None else controls,
            "a" * 40,
        )

    def test_reconciles_all_44_controls_and_660_dispositions(self):
        result = self.build()
        self.assertFalse(result["acceptance"])
        self.assertEqual(result["scope"], "PRE_CAPTURE_APPLICABILITY_ORACLE")
        self.assertEqual(result["summary"]["controls"], 44)
        self.assertEqual(result["summary"]["cells"], 43)
        self.assertEqual(result["summary"]["capabilityDispositions"], 660)
        self.assertEqual(result["summary"]["requiredAccepted"], 506)
        self.assertEqual(result["summary"]["requiredExcluded"], 154)
        self.assertEqual(len(result["controls"]), 44)
        for control in result["controls"]:
            self.assertEqual(set(control["capabilities"]), set(audit.CAPABILITIES))

    def test_generation_and_family_applicability_is_explicit(self):
        result = self.build()
        by_key = {
            (control["language"], control["family"], control["release"]): control
            for control in result["controls"]
        }
        gen1 = by_key[("en", "RED_BLUE", "RED_BLUE_CONTROL")]["capabilities"]
        self.assertEqual(sum(v["requiredFinalDisposition"] == "ACCEPTED" for v in gen1.values()), 9)
        self.assertEqual(gen1["MOVE_DESCRIPTIONS"]["reason"], "GEN1_HAS_NO_MOVE_DESCRIPTION_DOMAIN")
        self.assertEqual(gen1["WORLD_REGION_NAMES"]["reason"], "GRAPHICS_ONLY_WORLD_RASTER")

        gen2 = by_key[("en", "CRYSTAL", "CRYSTAL_CONTROL")]["capabilities"]
        self.assertEqual(sum(v["requiredFinalDisposition"] == "ACCEPTED" for v in gen2.values()), 10)
        self.assertEqual(gen2["ABILITY_NAMES"]["reason"], "PRE_ABILITY_GENERATION")

        ruby = by_key[("ja", "RUBY_SAPPHIRE", "RUBY_SAPPHIRE_CONTROL")]["capabilities"]
        self.assertTrue(all(v["requiredFinalDisposition"] == "ACCEPTED" for v in ruby.values()))

        firered = by_key[("ja", "FIRERED_LEAFGREEN", "FIRERED_LEAFGREEN_CONTROL")]["capabilities"]
        excluded = {key for key, value in firered.items() if value["requiredFinalDisposition"] == "EXCLUDED"}
        self.assertEqual(excluded, {"AREA_NAMES", "WORLD_REGION_NAMES"})

    def test_retained_poi_denominator_has_typed_missing_record_kinds(self):
        result = self.build()
        expected = [
            "RESERVED_SLOT",
            "NOT_APPLICABLE",
            "CONTEXTUAL_TEXT",
            "NO_TEXT",
            "UNRESOLVED",
        ]
        for control in result["controls"]:
            poi = control["capabilities"]["POI_TEXT"]
            self.assertEqual(poi["requiredFinalDisposition"], "ACCEPTED")
            self.assertEqual(poi["recordExclusionProofKinds"], expected)
            item = control["capabilities"]["ITEM_NAMES"]
            self.assertEqual(
                item["recordExclusionProofKinds"],
                ["RESERVED_SLOT", "NOT_APPLICABLE"],
            )

    def test_private_manifest_fields_are_not_copied(self):
        rendered = repr(self.build())
        self.assertNotIn("privatePath", rendered)
        self.assertNotIn("private/", rendered)
        self.assertNotIn("file", rendered)
        self.assertNotIn("header", rendered)
        self.assertNotIn("code", rendered)

    def test_rejects_incomplete_duplicate_or_wrong_rosters(self):
        controls = self.controls()
        with self.subTest("missing"):
            with self.assertRaisesRegex(ValueError, "CONTROL_ROSTER"):
                self.build(controls[:-1])
        with self.subTest("duplicate identity"):
            duplicate = [dict(control) for control in controls]
            duplicate[-1]["sha256"] = duplicate[0]["sha256"]
            with self.assertRaisesRegex(ValueError, "CONTROL_IDENTITY"):
                self.build(duplicate)
        with self.subTest("wrong Korean release"):
            wrong_release = [dict(control) for control in controls]
            wrong_release[-1]["release"] = "CRYSTAL"
            with self.assertRaisesRegex(ValueError, "CONTROL_ROSTER"):
                self.build(wrong_release)

    def test_rejects_invalid_source_commit(self):
        with self.assertRaisesRegex(ValueError, "SOURCE_COMMIT"):
            audit.build_audit(self.controls(), "main")


if __name__ == "__main__":
    unittest.main()
