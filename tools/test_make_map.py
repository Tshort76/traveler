import io
import json
import re
import unittest
import zipfile
from pathlib import Path

import make_map as mm

EXAMPLES = Path(__file__).resolve().parent.parent / "schema/examples"
DEMO = json.loads((EXAMPLES / "demo.trip.json").read_text())
LONG = json.loads((EXAMPLES / "long-synthetic.trip.json").read_text())


def names(marks):
    return [re.search(r"<name>(.*?)</name>", m).group(1) for m in marks]


class OverviewTest(unittest.TestCase):
    def test_the_home_airport_and_its_flights_are_left_out_but_the_far_airport_stays(self):
        shown = names(mm.overview(DEMO))
        self.assertNotIn("✈️ Los Angeles International Airport (LAX)", shown)
        self.assertIn("✈️ Ezeiza International Airport (EZE)", shown)
        self.assertEqual(["✈️ Buenos Aires · San Telmo → Puerto Madryn"], [n for n in shown if "→" in n])  # not the taxi across town

    def test_a_long_bus_ride_is_drawn_between_its_stations(self):
        trip = {"stays": [
            {"id": "salta", "name": "Salta", "arrive": "2027-01-01", "depart": "2027-01-03", "place": {"lat": -24.79, "lng": -65.41}},
            {"id": "cafayate", "name": "Cafayate", "arrive": "2027-01-03", "depart": "2027-01-05", "place": {"lat": -26.07, "lng": -65.98}},
        ], "transfers": [{"id": "bus", "from": "salta", "to": "cafayate", "date": "2027-01-03", "mode": "bus",
                          "fromPlace": {"name": "Terminal Salta", "lat": -24.79, "lng": -65.40},
                          "toPlace": {"name": "Terminal Cafayate", "lat": -26.08, "lng": -65.97}}]}
        shown = names(mm.overview(trip))
        self.assertEqual(["🚌 Salta → Cafayate", "🚌 Terminal Salta", "🚌 Terminal Cafayate"], [n for n in shown if "🚌" in n])

    def test_a_flight_is_drawn_between_its_airports(self):
        line = next(m for m in mm.overview(DEMO) if "→ Puerto Madryn" in m)
        self.assertIn("<coordinates>-58.4156,-34.5592,0 -65.2703,-43.2105,0</coordinates>", line)


class StayLayerTest(unittest.TestCase):
    def test_a_skipped_item_is_not_a_plan(self):
        days = mm.planned_days({"days": [{"date": "2027-10-19", "plan": [
            {"activityId": "a", "slot": "afternoon", "time": "14:30"}, {"activityId": "b", "status": "skipped"}]}]})
        self.assertEqual({"a": ["Tue 19 Oct afternoon 14:30"]}, days)

    def test_every_mapped_activity_is_in_exactly_one_layer_and_the_smallest_stays_merge_past_the_limit(self):
        for trip in (DEMO, LONG):
            with self.subTest(trip=trip["id"]):
                layers = mm.layers(trip)
                self.assertLessEqual(len(layers), mm.MAX_LAYERS)
                shown = sorted(n for _, marks in layers[1:] for n in names(marks))
                mapped = sorted(mm.esc(" ".join(x for x in [a.get("tag"), a["name"]] if x)) for a in trip["activities"] if mm.coords(a.get("place")))
                self.assertEqual(mapped, shown)
        self.assertEqual("Other stays", mm.layers(LONG)[-1][0])


class KmzTest(unittest.TestCase):
    def test_a_layer_carries_every_icon_its_styles_name(self):
        with zipfile.ZipFile(io.BytesIO(mm.kmz("Palermo", mm.activity_marks(DEMO, {"palermo"})))) as z:
            hrefs = set(re.findall(r"<href>(.*?)</href>", z.read("doc.kml").decode()))
            self.assertTrue(hrefs and hrefs <= set(z.namelist()))
            self.assertTrue(all(z.read(h).startswith(b"\x89PNG") for h in hrefs))


if __name__ == "__main__":
    unittest.main()
