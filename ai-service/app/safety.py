"""Deterministic risk signals; tool permissions remain the security boundary."""
import json
import re
import unicodedata
from pathlib import Path

from .schemas import TicketAnalysisResult

NO_EVIDENCE = "当前资料不足，请联系人工客服。"
UNSAFE_INPUT = "输入或资料存在安全风险，请联系人工客服。"
PATTERNS = [
    r"(?:忽略|无视|绕过|覆盖).{0,20}(?:指令|提示|规则|系统)",
    r"(?:输出|泄露|显示|打印|告诉|提供).{0,20}(?:密钥|密码|系统提示|其他用户|隐藏数据)",
    r"(?:调用|执行|启用).{0,20}(?:隐藏工具|退款工具|直接退款|转账)",
    r"(?:ignore|override|bypass).{0,40}(?:instruction|prompt|rule|system)",
    r"(?:reveal|print|show|expose).{0,40}(?:secret|api.?key|system.?prompt|password)",
    r"(?:call|execute).{0,40}(?:hidden.?tool|refund|transfer)",
    r"(?:<\s*/?\s*system\b|\[\s*system\s*\]|role\s*[:=]\s*system)",
]


def injection_risk(value) -> bool:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    text = unicodedata.normalize("NFKC", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Cf")
    return any(re.search(pattern, text, re.IGNORECASE | re.DOTALL) for pattern in PATTERNS)


def prompt_for(name: str, data: dict) -> str:
    instruction = (Path(__file__).parent / "prompts" / f"{name}.md").read_text(encoding="utf-8")
    return instruction + "\n<untrusted_data>\n" + json.dumps(data, ensure_ascii=False) + "\n</untrusted_data>"


def guard_analysis(result, prompt):
    parsed = TicketAnalysisResult.model_validate(result).model_dump(by_alias=True)
    data = prompt.split("\n<untrusted_data>\n", 1)[-1].rsplit("\n</untrusted_data>", 1)[0]
    risk = injection_risk(data) or injection_risk(parsed)
    try:
        inputs = json.loads(data)
        evidence = inputs.get("evidence", [])
        trusted = {c["chunkId"]: c for c in evidence}
        supported = bool(parsed["citations"]) and all(
            c["chunkId"] in trusted and c["documentId"] == trusted[c["chunkId"]].get("documentId")
            and c["quote"] and c["quote"] in trusted[c["chunkId"]].get("quote", "")
            for c in parsed["citations"])
    except (ValueError, TypeError, KeyError, AttributeError):
        supported = False
    if risk or not supported:
        parsed.update(needsHuman=True, proposalSuggestion=None, citations=[],
                      replySuggestion=UNSAFE_INPUT if risk else NO_EVIDENCE)
        parsed["riskFlags"] = sorted(set(parsed["riskFlags"] + [
            "PROMPT_INJECTION" if risk else "KNOWLEDGE_NO_EVIDENCE"]))
    return parsed
