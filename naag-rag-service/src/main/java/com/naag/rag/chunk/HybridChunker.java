package com.naag.rag.chunk;

import java.util.ArrayList;
import java.util.List;

public class HybridChunker {
    private final int maxChars;
    private final int overlapChars;
    private final int minChars;

    public HybridChunker(int maxChars, int overlapChars, int minChars) {
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
        this.minChars = minChars;
    }

    public List<String> chunk(String text) {
        text = normalize(text);
        if (text.isEmpty()) return List.of();

        String[] paras = text.split("\\n\\s*\\n+");
        List<String> base = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String p : paras) {
            p = p.trim();
            if (p.isEmpty()) continue;

            if (p.length() > maxChars) {
                flush(base, cur);
                splitLongParagraph(base, p);
                continue;
            }

            if (cur.length() + p.length() + 2 > maxChars) flush(base, cur);
            if (!cur.isEmpty()) cur.append("\n\n");
            cur.append(p);
        }
        flush(base, cur);

        return applyOverlap(base);
    }

    private void splitLongParagraph(List<String> out, String p) {
        String[] sents = p.split("(?<=[.!?])\\s+");
        StringBuilder cur = new StringBuilder();
        for (String s : sents) {
            if (cur.length() + s.length() + 1 > maxChars) {
                addIfBig(out, cur.toString());
                cur.setLength(0);
            }
            if (!cur.isEmpty()) cur.append(" ");
            cur.append(s.trim());
        }
        addIfBig(out, cur.toString());
    }

    private List<String> applyOverlap(List<String> chunks) {
        if (overlapChars <= 0 || chunks.size() <= 1) return chunks;

        List<String> out = new ArrayList<>(chunks.size());
        String prev = null;
        for (String c : chunks) {
            if (prev == null) out.add(c);
            else {
                String tail = prev.substring(Math.max(0, prev.length() - overlapChars));
                out.add(tail + "\n" + c);
            }
            prev = c;
        }
        return out;
    }

    private void flush(List<String> out, StringBuilder cur) {
        addIfBig(out, cur.toString());
        cur.setLength(0);
    }

    private void addIfBig(List<String> out, String s) {
        s = s == null ? "" : s.trim();
        if (s.length() >= minChars) out.add(s);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\u0000", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
