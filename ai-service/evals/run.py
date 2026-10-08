"""python -m evals.run [--real --chat] --output evals/reports/<name>.json"""
import argparse
import asyncio
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import math
from pathlib import Path

from app.agent import grounded_reply
from app.config import settings
from app.providers import embedding_provider, OpenAIChatProvider
from app.rag import select_evidence
from app.safety import injection_risk, prompt_for

ROOT = Path(__file__).parent
VERSION = "rag-eval-v1"
PRICE_SOURCE = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing"
EMBEDDING_PRICE_SOURCE = "https://www.siliconflow.cn/pricing"


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def eligible(chunk, case, now):
    if chunk.get("indexVersion") != 1:
        return False
    if any(chunk.get(k) != v for k, v in case["filters"].items()):
        return False
    for field, lower in [("effectiveFrom", True), ("effectiveTo", False)]:
        if chunk.get(field):
            date = datetime.fromisoformat(chunk[field].replace("Z", "+00:00"))
            if (now < date if lower else now >= date):
                return False
    return True


def cosine(a, b):
    denominator = math.sqrt(sum(v*v for v in a) * sum(v*v for v in b))
    if not denominator:
        raise ValueError("ZERO_VECTOR")
    return sum(x*y for x, y in zip(a, b)) / denominator


def metrics(cases, rankings, threshold):
    total = len(cases)
    positives = sum(case["answerable"] for case in cases)
    negatives = total - positives
    recalled = false_answers = correct = 0
    reciprocal = precision = 0.0
    for case in cases:
        hits = [] if injection_risk(case["query"]) else select_evidence(rankings[case["id"]], threshold)
        expected = set(case["expectedDocumentIds"])
        relevant = [hit["documentId"] in expected for hit in hits]
        if case["answerable"]:
            recalled += any(relevant)
            reciprocal += next((1/(i+1) for i, ok in enumerate(relevant) if ok), 0)
            precision += sum(relevant) / len(hits) if hits else 0
            correct += bool(hits)
        else:
            false_answers += bool(hits)
            correct += not hits
    return {"cases": total, "answerableCases": positives, "unanswerableCases": negatives,
            "recallAt5": recalled / positives if positives else 0,
            "mrrAt5": reciprocal / positives if positives else 0,
            "retrievalPrecisionAt5": precision / positives if positives else 0,
            "noAnswerFalseAcceptRate": false_answers / negatives if negatives else 0,
            "answerabilityAccuracy": correct / total if total else 0}


def scan_thresholds(cases, rankings, max_false_accept=0.0, min_recall=0.5):
    rows = [{"threshold": round(i/100, 2), **metrics(cases, rankings, i/100)} for i in range(101)]
    viable = [row for row in rows if row["noAnswerFalseAcceptRate"] <= max_false_accept
              and row["recallAt5"] >= min_recall]
    selected = max(viable, key=lambda r: (r["recallAt5"], r["retrievalPrecisionAt5"], r["threshold"])) if viable else None
    return rows, selected


def write_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(path)


class Budget:
    # Peak cache-miss rates verified 2026-10-08; reserve UTF-8 bytes as token upper bound
    # plus framing margin. No retries; reservations remain spent on unknown outcomes.
    def __init__(self, limit=20):
        self.limit = Decimal(str(limit))
        if not 0 < self.limit <= 20:
            raise ValueError("Budget must be between 0 and 20 CNY")
        self.reserved = Decimal(0)
        self.actual_upper = Decimal(0)

    def reserve(self, prompt):
        amount = (Decimal(len(prompt.encode("utf-8")) + 512)*9
                  + Decimal(settings.llm_max_tokens)*27) / 1_000_000
        if self.reserved + amount > self.limit:
            raise ValueError("EVAL_BUDGET_EXCEEDED")
        self.reserved += amount

    def record(self, usage):
        inputs, outputs = usage.get("prompt_tokens"), usage.get("completion_tokens")
        if type(inputs) is not int or type(outputs) is not int or inputs < 0 or outputs < 0:
            raise ValueError("EVAL_USAGE_UNKNOWN_STOP")
        self.actual_upper += (Decimal(inputs)*9 + Decimal(outputs)*27) / 1_000_000
        if self.actual_upper > self.reserved or self.actual_upper > self.limit:
            raise ValueError("EVAL_USAGE_EXCEEDS_RESERVATION_STOP")

    def summary(self):
        return {"limitCny": str(self.limit), "reservedCny": str(self.reserved),
                "usageCostUpperCny": str(self.actual_upper), "pricingVerifiedAt": "2026-10-08",
                "inputCnyPerMillion": "9", "outputCnyPerMillion": "27", "source": PRICE_SOURCE,
                "embeddingPriceCny": "0", "embeddingPriceSource": EMBEDDING_PRICE_SOURCE,
                "note": "保守高峰价估算，最终扣费以服务商账单为准；账户级硬限额需在服务商设置。"}


async def evaluate(args):
    if args.output.exists():
        raise ValueError("OUTPUT_EXISTS_USE_NEW_PATH")
    if args.chat and not args.real:
        raise ValueError("Chat evaluation requires --real")
    cases, corpus = read_jsonl(ROOT / "rag_cases.jsonl"), read_jsonl(ROOT / "corpus.jsonl")
    if len(cases) < 40 or len({c["id"] for c in cases}) != len(cases):
        raise ValueError("Invalid evaluation dataset")
    if args.real:
        if settings.embedding_provider != "siliconflow" or not settings.embedding_api_key:
            raise ValueError("Configure EMBEDDING_PROVIDER=siliconflow and EMBEDDING_API_KEY")
        if settings.embedding_model != "BAAI/bge-m3":
            raise ValueError("Unverified embedding price; this run supports BAAI/bge-m3 only")
        if args.chat and (settings.llm_provider != "deepseek" or not settings.llm_api_key
                          or settings.llm_chat_model != "deepseek-v4-pro"):
            raise ValueError("Configure DeepSeek key and deepseek-v4-pro before real chat evaluation")
        if datetime.now(timezone.utc).date().isoformat() != "2026-10-08":
            raise ValueError("Reverify provider prices and update pricing date before a paid evaluation")
    budget = Budget(args.budget_cny)
    now = datetime.now(timezone.utc)
    report = {"version": VERSION, "createdAt": now.isoformat(), "status": "RUNNING",
        "mode": "real" if args.real else "fake-regression", "caseCount": len(cases),
        "embeddingModel": settings.embedding_model if args.real else "fake-sha256",
        "embeddingDimension": settings.embedding_dimension if args.real else 8,
        "chatModel": settings.llm_chat_model if args.chat else None, "promptVersion": "chat-v3",
        "datasetSha256": hashlib.sha256((ROOT / "rag_cases.jsonl").read_bytes() + (ROOT / "corpus.jsonl").read_bytes()).hexdigest(),
        "promptSha256": hashlib.sha256((ROOT.parent / "app/prompts/chat_v3.md").read_bytes()).hexdigest(),
        "budget": budget.summary(), "chatResults": [], "cases": cases,
        "limitations": ["47条虚构演示资料，验证集较小；不能推断生产效果。",
                         "离线Cosine检索复用应用证据阈值；不测试Qdrant容量或Java实时授权。",
                         "事实包含规则是辅助检查，引用语义支持仍需人工复核。"]}
    write_report(args.output, report)
    try:
        if args.real:
            embedder = embedding_provider(settings.embedding_dimension)
        else:
            from app.providers import FakeEmbeddingProvider
            embedder = FakeEmbeddingProvider(8)
        texts = [c["quote"] for c in corpus] + [c["query"] for c in cases]
        vectors = []
        for start in range(0, len(texts), 16):
            vectors.extend(await embedder.embed_documents(texts[start:start+16]))
        rankings = {}
        for i, case in enumerate(cases):
            query = vectors[len(corpus)+i]
            rankings[case["id"]] = sorted([
                {**chunk, "score": cosine(query, vectors[j])}
                for j, chunk in enumerate(corpus) if eligible(chunk, case, now)],
                key=lambda hit: hit["score"], reverse=True)
        report["retrievalResults"] = [{"id": c["id"], "split": c["split"], "answerable": c["answerable"],
            "top1Score": rankings[c["id"]][0]["score"] if rankings[c["id"]] else None,
            "top3Scores": [h["score"] for h in rankings[c["id"]][:3]],
            "top5": [{"chunkId": h["chunkId"], "documentId": h["documentId"], "score": h["score"]}
                     for h in rankings[c["id"]][:5]]} for c in cases]
        calibration = [c for c in cases if c["split"] == "calibration"]
        validation = [c for c in cases if c["split"] == "validation"]
        rows, selected = scan_thresholds(calibration, rankings, args.max_false_accept, args.min_recall)
        report["thresholdScan"] = rows
        report["selectedThreshold"] = selected["threshold"] if selected else None
        report["calibration"] = selected
        threshold = selected["threshold"] if selected else None
        report["validation"] = metrics(validation, rankings, threshold)
        validation_ok = (selected is not None and report["validation"]["noAnswerFalseAcceptRate"] <= args.max_false_accept
                         and report["validation"]["recallAt5"] >= args.min_recall)
        report["thresholdApproved"] = bool(args.real and validation_ok)
        write_report(args.output, report)
        if args.chat:
            for case in cases:
                unsafe = injection_risk(case["query"])
                evidence = [] if unsafe else select_evidence(rankings[case["id"]], threshold, 3)
                # Evaluate provider resistance separately from application input rejection.
                prompt = prompt_for("chat_v3", {"message": case["query"], "evidence": evidence})
                budget.reserve(prompt)
                report["budget"] = budget.summary()
                write_report(args.output, report)  # reserve durably before any paid request
                model = OpenAIChatProvider()
                result = await model.structured(prompt)
                budget.record(model.usage)
                text, citations, human, flags = grounded_reply(result, evidence, unsafe)
                expected = set(case["expectedDocumentIds"])
                precision = sum(c["documentId"] in expected for c in citations)/len(citations) if citations else None
                facts = all(fact in text for fact in case["mustContainFacts"]) if case["answerable"] else human
                report["chatResults"].append({"id": case["id"], "split": case["split"],
                    "answerable": case["answerable"], "needsHuman": human, "riskFlags": flags,
                    "reply": text, "citationIds": [c["chunkId"] for c in citations],
                    "citationDocumentPrecision": precision, "requiredFactsPresent": facts,
                    "providerNeedsHuman": result.get("needsHuman"), "providerReply": result.get("replySuggestion"),
                    "usage": {k: model.usage.get(k) for k in ["prompt_tokens", "completion_tokens"]}})
                report["budget"] = budget.summary()
                write_report(args.output, report)
                print(f"Chat {len(report['chatResults'])}/{len(cases)} complete", flush=True)
            results = report["chatResults"]
            negatives = [r for r in results if not r["answerable"]]
            precision = [r["citationDocumentPrecision"] for r in results if r["citationDocumentPrecision"] is not None]
            report["chatMetrics"] = {"noAnswerFalseAnswerRate": sum(not r["needsHuman"] for r in negatives)/len(negatives),
                "answerabilityAccuracy": sum(r["answerable"] != r["needsHuman"] for r in results)/len(results),
                "requiredFactsAccuracy": sum(r["requiredFactsPresent"] for r in results)/len(results),
                "citationDocumentPrecision": sum(precision)/len(precision) if precision else None}
            attack_ids = {c["id"] for c in cases if c["category"] == "injection"}
            attacks = [r for r in results if r["id"] in attack_ids]
            report["chatMetrics"]["injectionHumanRate"] = sum(r["needsHuman"] for r in attacks)/len(attacks)
            report["chatMetrics"]["providerInjectionHumanRate"] = sum(r["providerNeedsHuman"] is True for r in attacks)/len(attacks)
        report["status"] = "COMPLETED" if validation_ok else "NO_ACCEPTABLE_THRESHOLD"
    except Exception as exc:
        report["status"] = "FAILED"
        report["errorType"] = type(exc).__name__  # never persist response body / credentials
        report["budget"] = budget.summary()
        write_report(args.output, report)
        raise RuntimeError("Evaluation stopped; inspect sanitized report") from None
    write_report(args.output, report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--real", action="store_true")
    parser.add_argument("--chat", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--budget-cny", type=float, default=20)
    parser.add_argument("--max-false-accept", type=float, default=0)
    parser.add_argument("--min-recall", type=float, default=0.5)
    args = parser.parse_args()
    if not 0 <= args.max_false_accept <= 1 or not 0 <= args.min_recall <= 1:
        parser.error("Metric constraints must be between 0 and 1")
    try:
        report = asyncio.run(evaluate(args))
    except (ValueError, RuntimeError) as exc:
        parser.exit(1, str(exc) + "\n")
    print(json.dumps({"status": report["status"], "selectedThreshold": report["selectedThreshold"],
                      "thresholdApproved": report["thresholdApproved"]}))
    if report["status"] != "COMPLETED":
        parser.exit(2)


if __name__ == "__main__":
    main()
