import unittest
import pandas as pd

from .data import coverage, members_on, tickers_since, yahoo_symbol


def membership(rows):
    return pd.DataFrame(dict(date=pd.to_datetime([d for d, _ in rows]), members=[frozenset(m) for _, m in rows]))


class DataTest(unittest.TestCase):
    def test_members_never_read_a_later_row(self):
        frame = membership([("2001-01-02", "AB"), ("2001-02-01", "AC")])
        self.assertEqual(members_on(frame, "2001-01-31"), frozenset("AB"))
        self.assertEqual(members_on(frame, "2001-02-01"), frozenset("AC"))
        with self.assertRaises(ValueError):
            members_on(frame, "2000-12-29")

    def test_tickers_since_include_members_already_in_index_at_start(self):
        frame = membership([("2000-06-01", "AB"), ("2000-12-01", "AC"), ("2001-03-01", "CD")])
        self.assertEqual(tickers_since(frame, "2001-01-01"), ["A", "C", "D"])

    def test_yahoo_share_class_symbol(self):
        self.assertEqual(yahoo_symbol("BRK.B"), "BRK-B")

    def test_coverage_counts_only_then_current_members_with_history(self):
        sessions = [d.date().isoformat() for d in pd.bdate_range("2000-01-03", "2001-03-30")]
        frame = membership([("2000-01-03", "AB"), ("2001-02-01", "AC")])
        prices = pd.DataFrame(dict(ticker="A", date=sessions))
        late = pd.DataFrame(dict(ticker="C", date=sessions[-5:]))
        table = coverage(frame, pd.concat([prices, late]), sessions).set_index("date")
        self.assertEqual(table.loc["2001-01-31", "pricedShare"], .5)  # B never priced
        self.assertEqual(table.loc["2001-03-30", "pricedShare"], 1)
        self.assertEqual(table.loc["2001-03-30", "historyShare"], .5)  # C lacks a year of bars


if __name__ == "__main__":
    unittest.main()
