"""Text normalization and prompt-injection screening for extracted CV text."""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

_ZERO_WIDTH = dict.fromkeys(map(ord, "​‌‍‎‏⁠﻿­"), None)
_CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]")
_SPACES = re.compile(r"[ \t  -   　]+")


def clean_text(raw: str) -> str:
    """NFKC-normalize, drop invisible/control characters, tidy whitespace."""
    text = unicodedata.normalize("NFKC", raw).translate(_ZERO_WIDTH)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = _CONTROL.sub(" ", text)
    lines = [_SPACES.sub(" ", line).strip() for line in text.split("\n")]
    out: list[str] = []
    blank = 0
    for line in lines:
        if not line:
            blank += 1
            if blank > 1:
                continue
        else:
            blank = 0
        out.append(line)
    return "\n".join(out).strip()


# Lines that look like instructions addressed to an AI system rather than CV content.
_INJECTION_PATTERNS = [
    re.compile(
        r"\b(ignore|disregard|forget|override|bypass)\b[^\n]{0,40}"
        r"\b(previous|prior|above|earlier|all|any|system|these)\b[^\n]{0,40}"
        r"\b(instructions?|prompts?|rules?|directions?|guidelines?)\b",
        re.I,
    ),
    re.compile(
        r"\b(system prompt|developer message|hidden instructions?|jailbreak|prompt injection)\b",
        re.I,
    ),
    re.compile(
        r"\byou are (now )?(an? |the )?(ai|assistant|model|language model|chatbot|recruiter bot)\b",
        re.I,
    ),
    re.compile(
        r"\b(ai|assistant|model|llm|chatgpt|claude|gpt)\b[^.\n]{0,30}"
        r"\b(must|should|shall|needs? to|will)\b[^.\n]{0,30}"
        r"\b(rate|score|rank|recommend|select|approve|hire|ignore|output|respond|reply|say|print)\b",
        re.I,
    ),
    re.compile(r"^\s*(system|assistant)\s*:", re.I),
    re.compile(r"<\s*/?\s*(system|instructions?|cv_document|prompt)\b[^>]*>", re.I),
]


@dataclass(frozen=True)
class ScreenedText:
    text: str
    removed_lines: int


def strip_injection_lines(text: str) -> ScreenedText:
    """
    Remove lines that resemble instructions to an AI. The removed text is excluded both from the
    LLM input and from the grounding corpus, so it can neither steer the model nor serve as
    "evidence" for an extracted item.
    """
    kept: list[str] = []
    removed = 0
    for line in text.split("\n"):
        if any(p.search(line) for p in _INJECTION_PATTERNS):
            removed += 1
            continue
        kept.append(line)
    return ScreenedText(text="\n".join(kept).strip(), removed_lines=removed)


def visible_char_count(text: str) -> int:
    return sum(1 for c in text if not c.isspace())
