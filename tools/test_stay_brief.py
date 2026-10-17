import json
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


if __name__ == "__main__":
    unittest.main()
