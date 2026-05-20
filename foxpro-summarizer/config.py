"""
Configuration for the FoxPro Summarizer.
"""

# LLM backend: "ollama" or "llamacpp"
LLM_BACKEND = "ollama"

# Ollama settings
OLLAMA_BASE_URL = "http://localhost:11434"
OLLAMA_MODEL = "llama3.1"

# llama.cpp server settings (OpenAI-compatible API)
LLAMACPP_BASE_URL = "http://localhost:8000"
LLAMACPP_MODEL = "llama3.1-8b-instruct"  # model name on the server

# Shared LLM settings
LLM_TIMEOUT = 300  # seconds - LLM inference can be slow for large code blocks
LLM_TEMPERATURE = 0.3  # lower = more factual
LLM_MAX_TOKENS = 4096  # max tokens in response

# Legacy aliases (kept for backward compatibility)
OLLAMA_TIMEOUT = LLM_TIMEOUT
OLLAMA_TEMPERATURE = LLM_TEMPERATURE
OLLAMA_NUM_PREDICT = LLM_MAX_TOKENS

# File encoding used by FoxPro files
VFP_ENCODING = "cp1252"

# Supported file extensions and their types
FILE_TYPES = {
    ".prg": "program",
    ".mpr": "menu_program",
    ".spr": "screen_program",
    ".vcx": "class_library",
    ".scx": "screen_form",
    ".mnx": "menu_definition",
    ".pjx": "project",
    ".frx": "report_form",
    ".h": "header",
}

# Max code length to send to LLM in a single request (characters)
MAX_CODE_LENGTH = 12000

# Report output format
REPORT_FORMAT = "markdown"  # markdown or html
