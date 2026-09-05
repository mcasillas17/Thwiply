#!/usr/bin/env python3
"""Require successful, non-skipped instrumentation results for the foundation suite."""

import argparse
from collections import Counter
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


REQUIRED_CLASSES = {
    "thwiply.elopenmike.com.BackupConfigurationTest",
    "thwiply.elopenmike.com.data.local.ThwiplyDatabaseTest",
    "thwiply.elopenmike.com.data.local.ThwiplyMigrationTest",
}


def verify_results(directory):
    reports = sorted(directory.rglob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"No instrumentation XML reports in {directory}")

    counts = Counter()
    seen = set()
    for report in reports:
        root = ET.parse(report).getroot()
        suites = list(root.iter("testsuite"))
        if not suites:
            raise ValueError(f"No test suites in {report}")
        for suite in suites:
            for outcome in ("failures", "errors", "skipped"):
                if int(suite.get(outcome, "0")) != 0:
                    raise ValueError(f"{report}: suite has {outcome}")
            cases = list(suite.findall("testcase"))
            if int(suite.get("tests", "-1")) != len(cases):
                raise ValueError(f"{report}: inconsistent test count")
            for case in cases:
                identity = (case.get("classname"), case.get("name"))
                if not all(identity) or identity in seen:
                    raise ValueError(f"{report}: missing or duplicate test identity {identity}")
                seen.add(identity)
                if any(case.find(outcome) is not None for outcome in ("failure", "error", "skipped")):
                    raise ValueError(f"{report}: unsuccessful test {identity}")
                counts[identity[0]] += 1

    missing = REQUIRED_CLASSES - counts.keys()
    if missing:
        raise ValueError(f"Required classes did not execute: {', '.join(sorted(missing))}")
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path, help="Fresh managed-device result directory")
    args = parser.parse_args()
    try:
        counts = verify_results(args.results)
    except (ValueError, OSError, ET.ParseError) as error:
        print(f"Instrumentation evidence failed: {error}", file=sys.stderr)
        return 1
    print("## Instrumentation results\n")
    for name, count in sorted(counts.items()):
        print(f"- `{name}`: {count} passed")
    print(f"\nTotal: {sum(counts.values())} passed; no failed or skipped tests.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
