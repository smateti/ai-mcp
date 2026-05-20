"""
Parses LLM responses into business and technical sections.

The LLM prompt asks for two clearly delimited sections:
  ## Business Summary
  ## Technical Details

This module splits the response text on those headers.
If parsing fails, the entire response is used as fallback for both.
"""
import re


def split_response(full_text):
    """
    Split an LLM response into business and technical parts.

    Returns:
        (business_summary, technical_summary) tuple of strings.
        If the expected headers are not found, both return the full text.
    """
    if not full_text:
        return ("", "")

    # Patterns to match the two top-level section headers
    biz_pattern = re.compile(r"^##\s+Business\s+Summary\s*$", re.IGNORECASE | re.MULTILINE)
    tech_pattern = re.compile(r"^##\s+Technical\s+Details\s*$", re.IGNORECASE | re.MULTILINE)

    biz_match = biz_pattern.search(full_text)
    tech_match = tech_pattern.search(full_text)

    if biz_match and tech_match:
        # Both headers found — split accordingly
        if biz_match.start() < tech_match.start():
            business = full_text[biz_match.end():tech_match.start()].strip()
            technical = full_text[tech_match.end():].strip()
        else:
            technical = full_text[tech_match.end():biz_match.start()].strip()
            business = full_text[biz_match.end():].strip()
        return (business, technical)

    if biz_match and not tech_match:
        # Only business header found — everything after it is business,
        # use full text for technical
        business = full_text[biz_match.end():].strip()
        return (business, full_text)

    if tech_match and not biz_match:
        # Only technical header found
        technical = full_text[tech_match.end():].strip()
        return (full_text, technical)

    # No headers found — use full text for both
    return (full_text, full_text)
