"""Tests for validate_trip.py. Run: cd tools && python3 -m unittest test_validate_trip"""
import json
import unittest
from pathlib import Path

import validate_trip as v

ROOT = Path(__file__).resolve().parent.parent


class ExamplesAreValid(unittest.TestCase):
    def test_every_example_validates_without_errors(self):
        files = sorted((ROOT / "schema" / "examples").glob("*.json"))
        self.assertGreaterEqual(len(files), 4)
        for f in files:
            with self.subTest(f.name):
                rep, _ = v.validate(json.loads(f.read_text()))
                self.assertEqual(rep.errors, [])


class CompletenessCheck(unittest.TestCase):
    def setUp(self):
        self.trip = json.loads((ROOT / "schema" / "examples" / "iguazu-short.trip.json").read_text())

    def errors(self, trip):
        rep = v.Report()
        v.check_complete(trip, rep)
        return rep.errors

    def test_the_example_is_complete(self):
        self.assertEqual(self.errors(self.trip), [])

    def test_missing_stars_and_loose_booking_advice_fail(self):
        a = self.trip["activities"][0]
        del a["stars"]
        a.pop("booking", None)
        a["practical"] = {"booking": "Buy ahead."}
        errs = " ".join(self.errors(self.trip))
        self.assertIn("have no stars", errs)
        self.assertIn("practical.booking", errs)

    def test_a_field_outside_the_format_and_a_non_usd_price_fail(self):
        a = self.trip["activities"][0]
        a["price"] = {"amount": 40}
        lodging = self.trip["stays"][0]["lodging"]
        lodging["price"] = {"amount": 90000, "currency": "ARS"}
        rep, _ = v.validate(self.trip)
        v.check_complete(self.trip, rep)
        errs = " ".join(rep.errors)
        self.assertIn(".price: not part of the format", errs)
        self.assertIn("prices are in USD", errs)

    def test_unbooked_lodging_needs_price_and_priority(self):
        lodging = self.trip["stays"][0]["lodging"]
        lodging["status"] = "tentative"
        lodging.pop("price", None)
        self.assertIn("needs price", " ".join(self.errors(self.trip)))


class InvalidFilesAreRejected(unittest.TestCase):
    REASONS = {
        "wrong-format": 'must be "traveler-trip"',
        "missing-stays": "stays",
        "bad-slot": "morning",
        "bad-timezone": "IANA",
        "unknown-activity-in-plan": "nope",
        "bad-date": "startDate",
        "depart-before-arrive": "depart is before arrive",
        "duplicate-activity-id": "cherry-creek",
        "unknown-stay": "boulder",
        "bad-price": "max is below amount",
        "price-not-object": "lodging.price: must be an object",
        "price-without-amount": "lodging.price: missing required field 'amount'",
        "night-price-off-lodging": '"night" is only for lodging',
    }

    def test_every_invalid_fixture_fails_for_its_own_reason(self):
        names = {f.name.removesuffix(".trip.json") for f in (ROOT / "schema" / "invalid").glob("*.json")}
        self.assertEqual(names - {"not-json"}, set(self.REASONS), "a new invalid fixture needs its reason here")
        with self.assertRaises(json.JSONDecodeError):
            json.loads((ROOT / "schema" / "invalid" / "not-json.trip.json").read_text())
        for name, expected in self.REASONS.items():
            with self.subTest(name):
                rep, _ = v.validate(json.loads((ROOT / "schema" / "invalid" / f"{name}.trip.json").read_text()))
                self.assertTrue(any(expected in e for e in rep.errors), rep.errors)


class Behaviour(unittest.TestCase):
    def setUp(self):
        self.trip = json.loads((ROOT / "schema" / "examples" / "minimal.trip.json").read_text())

    def test_unknown_field_is_a_warning_not_an_error(self):
        self.trip["stays"][0]["timezon"] = "America/Denver"
        rep, _ = v.validate(self.trip)
        self.assertEqual(rep.errors, [])
        self.assertTrue(any("timezon" in w for w in rep.warnings))

    def test_activity_needs_only_a_name(self):
        self.trip["activities"] = [{"id": "x", "stayId": "denver", "name": "Just a title"}]
        rep, _ = v.validate(self.trip)
        self.assertEqual(rep.errors, [])

    def test_missing_coordinates_warn(self):
        del self.trip["stays"][0]["place"]
        rep, _ = v.validate(self.trip)
        self.assertTrue(any("overview map" in w for w in rep.warnings))

    def test_fenced_paste_is_accepted(self):
        text = "Here you go:\n```json\n" + json.dumps(self.trip) + "\n```\n"
        self.assertEqual(json.loads(v.strip_fences(text))["id"], "weekend-denver")


if __name__ == "__main__":
    unittest.main()
