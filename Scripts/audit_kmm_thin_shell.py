#!/usr/bin/env python3
"""
Audit thin-shell ownership bypasses in the Android app.

Default mode is warning-only so Android scaffolding can land before the first
feature baseline is established. Pass --strict to exit non-zero when findings
remain.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

SCRIPT_DIR = Path(__file__).resolve().parent
ANDROID_ROOT = SCRIPT_DIR.parent

FEATURE_MODULE_ROOTS: tuple[Path, ...] = tuple(
    ANDROID_ROOT / "feature" / module / "src" / "main" / "kotlin"
    for module in (
        "auth",
        "onboarding",
        "home",
        "calendar",
        "logging",
        "care",
        "ai",
        "recommendations",
        "profile",
        "reports",
    )
)

BASELINE_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    (
        "Supabase or network clients",
        re.compile(
            r"\b(?:Supabase|Postgrest|Realtime|Functions|HttpClient|Retrofit|OkHttpClient|ApolloClient|ApiService)\b"
        ),
    ),
    (
        "Mutable business state",
        re.compile(
            r"\b(?:mutableStateOf|MutableStateFlow|mutableStateListOf|mutableStateMapOf|MutableSharedFlow)\b"
        ),
    ),
    (
        "Shared store references",
        re.compile(
            r"\b(?:AppStateStore|SessionManager|OnboardingFlowStore|CareStore|SyncStore|AuthRepository)\b"
        ),
    ),
    (
        "Shared policy usage",
        re.compile(
            r"\b(?:CycleMath|PeriodLogPolicy|SessionContext\.can|Permission\.)\b"
        ),
    ),
)


@dataclass(frozen=True)
class Finding:
    rule_id: str
    title: str
    path: str
    line_number: int
    snippet: str


@dataclass(frozen=True)
class AllowlistEntry:
    path: str
    reason: str


@dataclass(frozen=True)
class Rule:
    rule_id: str
    title: str
    description: str
    matcher: Callable[[str, str], bool]


# Keep this empty until a rule reaches 0 known findings and the owning checklist
# item is closed in the Android plan.
STRICT_RULE_IDS: frozenset[str] = frozenset()

RULE_ALLOWLISTS: dict[str, tuple[AllowlistEntry, ...]] = {
    "feature_direct_network_clients": (),
    "feature_viewmodel_edge_function_names": (),
    "feature_handrolled_cycle_period_logic": (),
    "feature_handrolled_permission_logic": (),
    "feature_local_business_state": (),
}


def iter_feature_kotlin_files() -> list[Path]:
    files: list[Path] = []
    for root in FEATURE_MODULE_ROOTS:
        if not root.exists():
            continue
        files.extend(sorted(root.rglob("*.kt")))
    return sorted(files)


def relative_path(path: Path) -> str:
    return path.relative_to(ANDROID_ROOT).as_posix()


def is_view_model_file(path: str) -> bool:
    normalized = f"/{path}"
    return "/viewmodel/" in normalized.lower() or path.endswith("ViewModel.kt")


def allowlist_entry_for(rule_id: str, path: str) -> AllowlistEntry | None:
    for entry in RULE_ALLOWLISTS.get(rule_id, ()):
        if entry.path.endswith("/"):
            if path.startswith(entry.path):
                return entry
        elif path == entry.path:
            return entry
    return None


def trimmed_snippet(line: str) -> str:
    return " ".join(line.strip().split())


def is_comment_line(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*") or stripped.startswith("*/")


def count_pattern(pattern: re.Pattern[str]) -> int:
    count = 0
    for path in iter_feature_kotlin_files():
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        count += len(pattern.findall(content))
    return count


def build_rules() -> list[Rule]:
    direct_network_pattern = re.compile(
        r"\b(?:Supabase|Postgrest|Realtime|Functions|HttpClient|Retrofit|OkHttpClient|ApolloClient|ApiService)\b"
    )
    edge_function_pattern = re.compile(
        r'(?:invoke|callEdgeFunction|runEdgeFunction|rpc|from)\s*\([^)\n]*"([A-Za-z0-9_-]+)"|functionName\s*=\s*"([A-Za-z0-9_-]+)"'
    )
    cycle_terms_pattern = re.compile(
        r"\b(?:cycle(?:Day|Length)?|period(?:Day|Length)?|ovulation|fertile|follicular|luteal|menstrual|phase|nextPeriod|lastPeriod)\b",
        re.IGNORECASE,
    )
    date_math_pattern = re.compile(
        r"\b(?:plusDays|minusDays|daysUntil|daysBetween|ChronoUnit\.DAYS\.between|Period\.between|Duration\.between)\b|%\s*(?:2[6-9]|[3-9][0-9])\b"
    )
    shared_cycle_pattern = re.compile(r"\b(?:CycleMath|PeriodLogPolicy)\b")
    permission_logic_pattern = re.compile(
        r"\b(?:permission|permissions|role|viewer|partnerMode|canView|canEdit|canShare)\b.*(?:==|!=|&&|\|\|)",
        re.IGNORECASE,
    )
    shared_permission_pattern = re.compile(r"\b(?:SessionContext\.can|\.can\s*\(\s*Permission\.)")
    local_business_state_pattern = re.compile(
        r"\b(?:val|var)\s+(?:appRoute|authState|session|onboardingState|careState|syncState|partnerHealth|phase|cycle|period|permissions?|reportData)\w*\b.*(?:mutableStateOf|MutableStateFlow|MutableSharedFlow|mutableStateListOf|mutableStateMapOf|by\s+mutableStateOf)",
        re.IGNORECASE,
    )

    return [
        Rule(
            rule_id="feature_direct_network_clients",
            title="Feature code directly references Supabase or network clients",
            description="Feature modules should talk to SakhiCore stores and Android platform adapters, not raw Supabase or network clients.",
            matcher=lambda path, line: bool(direct_network_pattern.search(line)),
        ),
        Rule(
            rule_id="feature_viewmodel_edge_function_names",
            title="Feature ViewModels embed backend or edge-function names",
            description="Feature ViewModels should not hardcode backend function names or table names.",
            matcher=lambda path, line: is_view_model_file(path)
            and bool(edge_function_pattern.search(line)),
        ),
        Rule(
            rule_id="feature_handrolled_cycle_period_logic",
            title="Feature code appears to hand-roll cycle or period logic",
            description="Cycle and period calculations should come from SakhiCore CycleMath and PeriodLogPolicy.",
            matcher=lambda path, line: bool(cycle_terms_pattern.search(line))
            and bool(date_math_pattern.search(line))
            and not bool(shared_cycle_pattern.search(line)),
        ),
        Rule(
            rule_id="feature_handrolled_permission_logic",
            title="Feature code appears to hand-roll permission decisions",
            description="Feature code should gate care and role access through SessionContext.can and Permission.",
            matcher=lambda path, line: bool(permission_logic_pattern.search(line))
            and not bool(shared_permission_pattern.search(line)),
        ),
        Rule(
            rule_id="feature_local_business_state",
            title="Feature code owns mutable business state instead of observing shared stores",
            description="Business state should come from AppStateStore, SessionManager, OnboardingFlowStore, CareStore, SyncStore, or AuthRepository flows.",
            matcher=lambda path, line: bool(local_business_state_pattern.search(line)),
        ),
    ]


def collect_findings(
    rules: list[Rule],
) -> tuple[dict[str, list[Finding]], dict[str, dict[tuple[str, str], int]]]:
    findings: dict[str, list[Finding]] = {rule.rule_id: [] for rule in rules}
    suppressed: dict[str, dict[tuple[str, str], int]] = {rule.rule_id: {} for rule in rules}

    for path in iter_feature_kotlin_files():
        rel_path = relative_path(path)
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue

        for line_number, line in enumerate(lines, start=1):
            if is_comment_line(line):
                continue
            for rule in rules:
                if rule.matcher(rel_path, line):
                    allowlist_entry = allowlist_entry_for(rule.rule_id, rel_path)
                    if allowlist_entry is not None:
                        key = (allowlist_entry.path, allowlist_entry.reason)
                        suppressed[rule.rule_id][key] = suppressed[rule.rule_id].get(key, 0) + 1
                        continue
                    findings[rule.rule_id].append(
                        Finding(
                            rule_id=rule.rule_id,
                            title=rule.title,
                            path=rel_path,
                            line_number=line_number,
                            snippet=trimmed_snippet(line),
                        )
                    )

    return findings, suppressed


def print_baseline_counts() -> None:
    print("Baseline ownership signals")
    for label, pattern in BASELINE_PATTERNS:
        print(f"- {label}: {count_pattern(pattern)}")
    print()


def print_findings(
    rules: list[Rule],
    findings: dict[str, list[Finding]],
    suppressed: dict[str, dict[tuple[str, str], int]],
) -> int:
    total = 0
    print("Tracked thin-shell bypasses")
    for rule in rules:
        rule_findings = findings[rule.rule_id]
        total += len(rule_findings)
        is_strict = rule.rule_id in STRICT_RULE_IDS
        level = "[FAIL]" if (is_strict and rule_findings) else "[warning]"
        strict_label = " [strict - ownership complete]" if is_strict else ""
        print(f"{level} {rule.rule_id} ({len(rule_findings)}){strict_label}")
        print(f"  {rule.description}")
        if suppressed[rule.rule_id]:
            print("  allowlisted:")
            for (path, reason), count in sorted(suppressed[rule.rule_id].items()):
                print(f"  - {path} ({count})  {reason}")
        for finding in rule_findings:
            print(f"  - {finding.path}:{finding.line_number}  {finding.snippet}")
        print()
    return total


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit the Sakhi Android app for KMM thin-shell ownership bypasses."
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit non-zero if any tracked bypass is still present.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not ANDROID_ROOT.exists():
        print(f"ERROR: Android root not found at {ANDROID_ROOT}", file=sys.stderr)
        return 2

    rules = build_rules()
    findings, suppressed = collect_findings(rules)
    total_findings = sum(len(rule_findings) for rule_findings in findings.values())

    print("KMM thin-shell audit")
    print(f"- Android root: {ANDROID_ROOT}")
    print(f"- Scanned feature roots: {len(FEATURE_MODULE_ROOTS)}")
    print(f"- Kotlin files found: {len(iter_feature_kotlin_files())}")
    print(f"- Mode: {'strict' if args.strict else 'warning-only'}")
    print()
    print_baseline_counts()
    print_findings(rules, findings, suppressed)

    strict_failures = sum(
        len(findings[rule_id])
        for rule_id in STRICT_RULE_IDS
        if rule_id in findings
    )

    if total_findings == 0:
        print("Result: clean, no tracked thin-shell bypasses found.")
        return 0

    if strict_failures > 0:
        print(f"Result: FAIL, {strict_failures} bypass(es) in completed-ownership areas.")
        return 1

    if args.strict:
        print(f"Result: FAIL, {total_findings} tracked bypasses remain.")
        return 1

    print(f"Result: WARNING ONLY, {total_findings} tracked bypasses remain.")
    print("Use --strict to turn these warnings into a failing audit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
