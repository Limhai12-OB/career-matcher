"""OllamaProvider: request shape, parsing and error mapping against a mocked Ollama server."""

import json

import httpx2
import pytest

from app.analysis.factory import build_provider
from app.analysis.prompt import SYSTEM_PROMPT
from app.analysis.providers.base import ProviderError, ProviderErrorCode
from app.analysis.providers.ollama_provider import OllamaProvider
from app.analysis.schemas import CvExtraction
from app.config import Settings
from app.explain.schemas import ExplanationOut
from tests.conftest import TEST_TOKEN
from tests.fakes import extraction
from tests.test_explain import facts

BASE = "http://ollama.test"


def settings(**overrides) -> Settings:
    values = {"AI_INTERNAL_TOKEN": TEST_TOKEN, "APP_ENV": "test", "AI_PROVIDER": "ollama"}
    values.update(overrides)
    return Settings(**values)


def provider_with(handler) -> tuple[OllamaProvider, list[httpx2.Request]]:
    seen: list[httpx2.Request] = []

    def record(request: httpx2.Request) -> httpx2.Response:
        seen.append(request)
        return handler(request)

    client = httpx2.AsyncClient(base_url=BASE, transport=httpx2.MockTransport(record))
    return OllamaProvider(settings(), client=client), seen


def chat_reply(content: str, done_reason: str = "stop") -> httpx2.Response:
    return httpx2.Response(
        200,
        json={
            "model": "qwen2.5:7b",
            "message": {"role": "assistant", "content": content},
            "done": True,
            "done_reason": done_reason,
            "prompt_eval_count": 100,
            "eval_count": 50,
            "total_duration": 1_000_000_000,
        },
    )


SAMPLE = extraction(
    skills=[{"name": "Python", "category": "PROGRAMMING_LANGUAGE", "evidence": "Python, SQL"}]
)


async def test_extract_sends_schema_constrained_request_and_parses_result():
    provider, seen = provider_with(lambda r: chat_reply(SAMPLE.model_dump_json()))

    result = await provider.extract("Skills: Python, SQL")

    assert result.extraction == SAMPLE
    assert result.model == "qwen2.5:7b"
    body = json.loads(seen[0].content)
    assert seen[0].url.path == "/api/chat"
    assert body["model"] == "qwen2.5:7b"
    assert body["stream"] is False
    assert body["format"] == CvExtraction.model_json_schema()
    assert body["options"]["temperature"] == 0
    assert body["messages"][0] == {"role": "system", "content": SYSTEM_PROMPT}
    # CV text is only ever in the user turn, inside the untrusted-data wrapper.
    assert "Skills: Python, SQL" in body["messages"][1]["content"]
    assert "Skills: Python, SQL" not in body["messages"][0]["content"]


async def test_output_violating_constraints_is_invalid_and_retryable():
    bad = json.dumps({**json.loads(SAMPLE.model_dump_json()), "unexpected": 1})
    provider, _ = provider_with(lambda r: chat_reply(bad))

    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == ProviderErrorCode.AI_INVALID_OUTPUT
    assert err.value.retryable


async def test_non_json_output_is_invalid():
    provider, _ = provider_with(lambda r: chat_reply("Sure! Here is the JSON"))
    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == ProviderErrorCode.AI_INVALID_OUTPUT


async def test_truncated_output_is_invalid():
    provider, _ = provider_with(lambda r: chat_reply('{"skills": [', done_reason="length"))
    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == ProviderErrorCode.AI_INVALID_OUTPUT


@pytest.mark.parametrize(
    ("status", "code"),
    [(404, ProviderErrorCode.AI_NOT_CONFIGURED), (500, ProviderErrorCode.AI_UNAVAILABLE)],
)
async def test_http_errors_are_mapped(status, code):
    provider, _ = provider_with(lambda r: httpx2.Response(status, json={"error": "x"}))
    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == code


async def test_connection_failure_is_unavailable():
    def refuse(request):
        raise httpx2.ConnectError("refused", request=request)

    provider, _ = provider_with(refuse)
    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == ProviderErrorCode.AI_UNAVAILABLE


async def test_timeout_is_mapped():
    def slow(request):
        raise httpx2.ReadTimeout("slow", request=request)

    provider, _ = provider_with(slow)
    with pytest.raises(ProviderError) as err:
        await provider.extract("cv")
    assert err.value.code == ProviderErrorCode.AI_TIMEOUT


async def test_explain_uses_explanation_schema():
    out = ExplanationOut(
        summary="Your skills cover most of this role.", strengths=[], gaps=[], next_steps=[]
    )
    provider, seen = provider_with(lambda r: chat_reply(out.model_dump_json()))
    result, model = await provider.explain(facts())

    assert result == out
    assert model == "qwen2.5:7b"
    assert json.loads(seen[0].content)["format"] == ExplanationOut.model_json_schema()


def test_factory_builds_ollama_provider():
    provider = build_provider(settings())
    assert isinstance(provider, OllamaProvider)
    assert provider.name == "ollama"


def test_ollama_is_refused_in_production():
    with pytest.raises(ValueError, match="local development only"):
        settings(APP_ENV="production")
