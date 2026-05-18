"""
Configuration for the FoxPro Summarizer.
"""

# Ollama / Llama 3.1 settings
OLLAMA_BASE_URL = "http://localhost:11434"
OLLAMA_MODEL = "llama3.1"
OLLAMA_TIMEOUT = 300  # seconds - LLM inference can be slow for large code blocks
OLLAMA_TEMPERATURE = 0.3  # lower = more factual
OLLAMA_NUM_PREDICT = 4096  # max tokens in response

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
