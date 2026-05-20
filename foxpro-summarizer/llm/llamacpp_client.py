"""
llama.cpp server client — uses the OpenAI-compatible /v1/chat/completions endpoint
exposed by llama-server (formerly llama.cpp server).

Drop-in alternative to OllamaClient with the same public interface:
    test_connection(), generate(prompt), summarize_foxpro(code, context, python)
"""
import json
import logging
import requests

from config import (
    LLAMACPP_BASE_URL,
    LLAMACPP_MODEL,
    LLM_TIMEOUT,
    LLM_TEMPERATURE,
    LLM_MAX_TOKENS,
)

logger = logging.getLogger(__name__)


class LlamaCppClient:
    """Client for llama.cpp's OpenAI-compatible REST API."""

    def __init__(self, base_url=None, model=None):
        self.base_url = (base_url or LLAMACPP_BASE_URL).rstrip("/")
        self.model = model or LLAMACPP_MODEL  # may be empty — server uses loaded model

    def test_connection(self):
        """Test if llama.cpp server is running and responding."""
        try:
            # llama.cpp exposes /health or /v1/models
            resp = requests.get(f"{self.base_url}/v1/models", timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                models = data.get("data", [])
                if models:
                    model_ids = [m.get("id", "") for m in models]
                    logger.info(
                        "Connected to llama.cpp server. Models: %s",
                        ", ".join(model_ids),
                    )
                    # If no model was specified, try to pick a chat-capable one
                    if not self.model and model_ids:
                        # Skip known embedding/reranker models
                        _skip = {"all-minilm", "bge-reranker", "nomic-embed", "e5-"}
                        chat_models = [
                            m for m in model_ids
                            if not any(s in m.lower() for s in _skip)
                        ]
                        self.model = chat_models[0] if chat_models else model_ids[0]
                        logger.info("Auto-selected model: %s", self.model)
                    elif self.model and self.model not in model_ids:
                        # Configured model not found — warn but continue
                        logger.warning(
                            "Configured model '%s' not found on server. "
                            "Available: %s", self.model, ", ".join(model_ids),
                        )
                else:
                    logger.info("Connected to llama.cpp server (model loaded).")
                return True

            # Fallback: try /health endpoint
            resp = requests.get(f"{self.base_url}/health", timeout=10)
            if resp.status_code == 200:
                logger.info("Connected to llama.cpp server (health OK).")
                return True

        except requests.ConnectionError:
            logger.error("Cannot connect to llama.cpp server at %s", self.base_url)
        except Exception as e:
            logger.error("llama.cpp connection test failed: %s", e)
        return False

    def generate(self, prompt):
        """Send a prompt to llama.cpp and return the response text."""
        payload = {
            "messages": [
                {"role": "user", "content": prompt}
            ],
            "temperature": LLM_TEMPERATURE,
            "max_tokens": LLM_MAX_TOKENS,
            "top_p": 0.9,
            "stream": False,
        }

        # Include model if specified (otherwise server uses its loaded model)
        if self.model:
            payload["model"] = self.model

        resp = requests.post(
            f"{self.base_url}/v1/chat/completions",
            json=payload,
            timeout=LLM_TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()

        # OpenAI-compatible response format
        choices = data.get("choices", [])
        if choices:
            message = choices[0].get("message", {})
            content = message.get("content", "")
            if content:
                return content

        raise ValueError(f"Unexpected response format: {json.dumps(data)[:500]}")

    def summarize_foxpro(self, code, context, include_python=None):
        """
        Summarize a FoxPro code block with rich context.
        Same interface as OllamaClient.summarize_foxpro().
        """
        prompt = self._build_prompt(code, context, include_python)
        return self.generate(prompt)

    def _build_prompt(self, code, context, python_code=None):
        """Build a detailed prompt for FoxPro analysis."""
        sections = [
            "You are a software analyst expert in Visual FoxPro (VFP) applications.",
            "Analyze the following FoxPro source code and provide a structured summary.",
            "You MUST organize your response into exactly two top-level sections:",
            "",
            f"Context: {context}",
            "",
            "## Business Summary",
            "",
            "Write this section for business stakeholders (non-technical readers):",
            "",
            "### Purpose & Use Case",
            "What business function does this code serve? Describe in plain English.",
            "",
            "### Business Rules & Validations",
            "What rules, constraints, or validations are enforced?",
            "",
            "### User Interactions",
            "What inputs, outputs, messages, or forms does the user see?",
            "",
            "## Technical Details",
            "",
            "Write this section for developers:",
            "",
            "### Key Operations",
            "List the main operations (CRUD, calculations, validations, navigation).",
            "",
            "### Data Tables & Fields",
            "Which .dbf tables are opened/queried and what fields are used?",
            "",
            "### Dependencies",
            "What other modules, procedures, forms, or class libraries does it call?",
            "",
            "### Access Control",
            "Are there any access level checks or permission-based logic?",
            "",
            "---",
            "",
            "FoxPro Source Code:",
            "```foxpro",
            code,
            "```",
        ]

        if python_code:
            sections.extend([
                "",
                "For reference, here is the Python equivalent (auto-converted):",
                "```python",
                python_code[:3000],
                "```",
            ])

        sections.extend([
            "",
            "IMPORTANT: You must include both '## Business Summary' and "
            "'## Technical Details' headers in your response. Use bullet points "
            "and short paragraphs. Focus on business logic, not syntax.",
        ])

        return "\n".join(sections)
