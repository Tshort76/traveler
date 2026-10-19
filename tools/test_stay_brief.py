import json
import shutil
import tempfile
import unittest
from pathlib import Path

import stay_brief as sb

DEMO = json.loads((Path(__file__).resolve().parent.parent / "schema/examples/demo.trip.json").read_text())


class FindTest(unittest.TestCase):
    def test_a_query_finds_the_stays_it_names(self):
        stays = sb.stays_of(DEMO)
        for query, ids in [
            ("Puerto Madryn", ["madryn"]), ("madryn", ["madryn"]), ("3", ["madryn"]),
            ("buenos aires", ["palermo", "san-telmo"]), ("Los Angeles", []), ("Ushuaia", []),
        ]:
            with self.subTest(query=query):
                self.assertEqual([s["id"] for s in sb.find(stays, query)], ids)


class BriefTest(unittest.TestCase):
    def test_a_stay_lists_its_own_standouts_and_their_days(self):
        text = sb.brief(DEMO, next(s for s in DEMO["stays"] if s["id"] == "madryn"))
        self.assertIn("Puerto Madryn waterfront rambla · Fri 22 Oct afternoon", text)
        self.assertNotIn("Punta Cuevas", text)  # 2 stars
        self.assertNotIn("Teatro Colón", text)  # another stay


class TripsInTest(unittest.TestCase):
    def test_a_trip_is_found_by_its_contents_and_the_newest_copy_wins(self):
        examples = Path(__file__).resolve().parent.parent / "schema/examples"
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "chat").mkdir()
            shutil.copy(examples / "minimal.trip.json", Path(d) / "minimal.trip.json")
            (Path(d) / "notes.json").write_text('{"title": "not a trip"}')
            newer = dict(DEMO, exportedFrom={"exportedAt": "2099-01-01T00:00:00Z"})
            (Path(d) / "demo.trip.json").write_text(json.dumps(DEMO))
            (Path(d) / "chat" / "demo.trip (1).json").write_text(json.dumps(newer))
            found = sb.trips_in([d])
            self.assertEqual([f.name for f, _ in found], ["minimal.trip.json", "demo.trip (1).json"])
            self.assertEqual([f.name for f, _ in sb.pick_trip("demo", [d])], ["demo.trip (1).json"])


if __name__ == "__main__":
    unittest.main()
