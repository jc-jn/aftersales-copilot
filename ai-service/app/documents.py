import base64, hashlib, io, re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

@dataclass
class DocumentChunk:
    sequence_no: int
    section_title: str
    content: str
    page: int | None = None

def sha256_bytes(data: bytes) -> str: return hashlib.sha256(data).hexdigest()

def decode_document(content_base64: str | None, file_name: str) -> bytes:
    if not content_base64: raise ValueError("DOCUMENT_CONTENT_REQUIRED")
    try: return base64.b64decode(content_base64, validate=True)
    except Exception as exc: raise ValueError("DOCUMENT_INVALID_BASE64") from exc

def parse_document(data: bytes, file_name: str, content_type: str) -> str:
    suffix=Path(file_name).suffix.lower()
    if suffix in {".md", ".txt"} or content_type.startswith("text/"):
        return data.decode("utf-8")
    if suffix == ".pdf":
        try:
            from pypdf import PdfReader
            reader=PdfReader(io.BytesIO(data))
            if reader.is_encrypted: raise ValueError("DOCUMENT_UNSUPPORTED_ENCRYPTED")
            return "\n\n".join(page.extract_text() or "" for page in reader.pages)
        except ImportError as exc: raise ValueError("DOCUMENT_PARSER_UNAVAILABLE") from exc
    if suffix == ".docx":
        try:
            from docx import Document
            return "\n\n".join(p.text for p in Document(io.BytesIO(data)).paragraphs)
        except ImportError as exc: raise ValueError("DOCUMENT_PARSER_UNAVAILABLE") from exc
    raise ValueError("DOCUMENT_UNSUPPORTED")

def clean_text(text: str) -> str:
    text=re.sub(r"\r\n?", "\n", text); text=re.sub(r"[ \t]+", " ", text); text=re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()

def chunk_document(text: str, target: int = 700, overlap: int = 100) -> list[DocumentChunk]:
    text=clean_text(text)
    sections=re.split(r"(?m)(?=^#{1,6}\s+)", text)
    chunks=[]; seq=0
    for section in sections:
        section=section.strip()
        if not section: continue
        lines=section.splitlines(); title=re.sub(r"^#{1,6}\s+", "", lines[0]).strip() if lines and lines[0].startswith("#") else ""
        body=section
        start=0
        while start < len(body):
            end=min(len(body),start+target); piece=body[start:end].strip()
            if piece: chunks.append(DocumentChunk(seq,title,piece,None)); seq += 1
            if end >= len(body): break
            start=max(start+1,end-overlap)
    return chunks
