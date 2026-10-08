from datetime import datetime, timezone
from decimal import Decimal

import pytest

from evals.run import Budget, ROOT, eligible, metrics, read_jsonl, scan_thresholds


def hit(score, document="correct"):
    return {"chunkId": document, "documentId": document, "quote": "证据", "score": score}


def test_scan_selects_safe_threshold_and_reports_impossible_tradeoff():
    cases = [{"id": "yes", "query": "政策", "answerable": True, "expectedDocumentIds": ["correct"]},
             {"id": "no", "query": "天气", "answerable": False, "expectedDocumentIds": []}]
    ranks = {"yes": [hit(0.9)], "no": [hit(0.7, "wrong")]}
    rows, selected = scan_thresholds(cases, ranks)
    assert len(rows) == 101 and selected["threshold"] == 0.9
    assert selected["recallAt5"] == 1 and selected["noAnswerFalseAcceptRate"] == 0
    ranks["no"] = [hit(0.95, "wrong")]
    assert scan_thresholds(cases, ranks)[1] is None
    assert metrics(cases, ranks, None)["answerabilityAccuracy"] == 0.5


def test_dataset_has_expected_coverage_and_filter_boundaries():
    cases = read_jsonl(ROOT / "rag_cases.jsonl")
    corpus = read_jsonl(ROOT / "corpus.jsonl")
    assert len(cases) >= 40 and len({c["id"] for c in cases}) == len(cases)
    documents = {c["documentId"] for c in corpus}
    assert all(set(c["expectedDocumentIds"]) <= documents for c in cases)
    for split in ["calibration", "validation"]:
        subset = [c for c in cases if c["split"] == split]
        assert any(c["answerable"] for c in subset) and any(not c["answerable"] for c in subset)
    now = datetime(2026, 10, 8, tzinfo=timezone.utc)
    case = {"filters": {"scopeType": "GLOBAL"}}
    assert not eligible(next(c for c in corpus if c["chunkId"] == "old-version-1"), case, now)
    assert not eligible(next(c for c in corpus if c["chunkId"] == "expired-1"), case, now)
    assert not eligible(next(c for c in corpus if c["chunkId"] == "wrong-scope-1"), case, now)


def test_budget_reserves_before_request_and_unknown_usage_stops():
    budget = Budget(0.02)
    budget.reserve("政策")
    assert budget.reserved > 0
    with pytest.raises(ValueError, match="BUDGET"):
        budget.reserve("政策")
    with pytest.raises(ValueError, match="USAGE_UNKNOWN"):
        budget.record({})
    budget.record({"prompt_tokens": 10, "completion_tokens": 10})
    assert budget.actual_upper == Decimal("0.00036")
    with pytest.raises(ValueError):
        Budget(21)
