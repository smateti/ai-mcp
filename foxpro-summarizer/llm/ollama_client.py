"""
Ollama REST API client for sending FoxPro code to Llama 3.1 for summarization.
"""
import json
import logging
import requests

from config import (
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    OLLAMA_TIMEOUT,
    OLLAMA_TEMPERATURE,
    OLLAMA_NUM_PREDICT,
)

logger = logging.getLogger(__name__)


class OllamaClient:
    """Client for Ollama's local REST API."""

    def __init__(self, base_url=None, model=None):
        self.base_url = base_url or OLLAMA_BASE_URL
        self.model = model or OLLAMA_MODEL

    def test_connection(self):
        """Test if Ollama is running and the model is available."""
        try:
            resp = requests.get(f"{self.base_url}/api/tags", timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                model_names = [m.get("name", "") for m in data.get("models", [])]
                # Check if model name matches (with or without :latest tag)
                found = any(
                    self.model in name or name.startswith(self.model)
                    for name in model_names
                )
                if found:
                    logger.info("Connected to Ollama. Model '%s' available.", self.model)
                    return True
                else:
                    logger.error(
                        "Model '%s' not found. Available: %s",
                        self.model,
                        ", ".join(model_names),
                    )
                    return False
        except requests.ConnectionError:
            logger.error("Cannot connect to Ollama at %s", self.base_url)
        except Exception as e:
            logger.error("Ollama connection test failed: %s", e)
        return False

    def generate(self, prompt):
        """Send a prompt to Ollama and return the response text."""
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": OLLAMA_TEMPERATURE,
                "num_predict": OLLAMA_NUM_PREDICT,
                "top_p": 0.9,
            },
        }

        resp = requests.post(
            f"{self.base_url}/api/generate",
            json=payload,
            timeout=OLLAMA_TIMEOUT,
        )
        resp.raise_for_status()
        data = resp.json()

        if "response" in data:
            return data["response"]
        raise ValueError(f"Unexpected response format: {json.dumps(data)[:500]}")

    def summarize_foxpro(self, code, context, include_python=None):
        """
        Summarize a FoxPro code block with rich context.
        Optionally includes the Python-converted equivalent for comparison.
        """
        prompt = self._build_prompt(code, context, include_python)
        return self.generate(prompt)

    def _build_prompt(self, code, context, python_code=None):
        """Build a detailed prompt for FoxPro analysis."""
        sections = [
            "You are a software analyst expert in Visual FoxPro (VFP) applications.",
            "Analyze the following FoxPro source code and provide a structured, "
            "human-readable summary.",
            "",
            f"Context: {context}",
            "",
            "Provide the following sections:",
            "",
            "## Use Case / Purpose",
            "What business function does this code serve? Describe in plain English.",
            "",
            "## Key Operations",
            "List the main operations (CRUD, calculations, validations, navigation).",
            "",
            "## Data Tables & Fields",
            "Which .dbf tables are opened/queried and what fields are used?",
            "",
            "## Business Rules & Validations",
            "What rules, constraints, or validations are enforced?",
            "",
            "## User Interface & Interactions",
            "What inputs, outputs, messages, or forms are involved?",
            "",
            "## Dependencies",
            "What other modules, procedures, forms, or class libraries does it call?",
            "",
            "## Access Control",
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
            "Provide a clear, well-organized summary. Use bullet points and "
            "short paragraphs. Focus on business logic, not syntax.",
        ])

        return "\n".join(sections)
