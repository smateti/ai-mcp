package com.example.conjur.web.rest;

class PageTemplates {

    static final String CSS = """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                   max-width: 960px; margin: 0 auto; padding: 20px;
                   background: #f5f5f5; color: #333; }
            h1 { color: #1a1a2e; border-bottom: 2px solid #16213e; padding-bottom: 10px; }
            h2 { color: #16213e; margin-top: 0; }
            nav { background: #16213e; padding: 10px 20px; border-radius: 8px; margin-bottom: 20px; }
            nav a { color: #e0e0e0; text-decoration: none; margin-right: 20px; font-weight: 500; }
            nav a:hover, nav a.active { color: #fff; text-decoration: underline; }
            .card { background: #fff; border-radius: 8px; padding: 20px; margin: 16px 0;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            table { border-collapse: collapse; width: 100%%; }
            th, td { text-align: left; padding: 10px 14px; border-bottom: 1px solid #eee; }
            th { background: #f0f0f0; font-weight: 600; }
            .badge { display: inline-block; padding: 3px 12px; border-radius: 12px;
                     font-size: 0.85em; font-weight: 600; }
            .badge-ok  { background: #d4edda; color: #155724; }
            .badge-err { background: #f8d7da; color: #721c24; }
            .badge-warn { background: #fff3cd; color: #856404; }
            code.masked { cursor: pointer; background: #333; color: #333;
                          padding: 2px 8px; border-radius: 3px; user-select: none; }
            code.masked.revealed { background: #e9ecef; color: #333; }
            .form-row { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin: 12px 0; }
            .form-group { display: flex; flex-direction: column; }
            .form-group label { font-weight: 600; margin-bottom: 4px; font-size: 0.9em; }
            .form-group input, .form-group select { padding: 8px 12px; border: 1px solid #ccc; border-radius: 4px; min-width: 200px; }
            button { padding: 8px 20px; background: #0f3460; color: #fff; border: none;
                     border-radius: 4px; cursor: pointer; font-size: 0.95em; height: 38px; }
            button:hover { background: #16213e; }
            button.danger { background: #c0392b; }
            button.danger:hover { background: #96281b; }
            .result { margin-top: 10px; padding: 12px; background: #e9ecef; border-radius: 4px;
                      font-family: monospace; white-space: pre-wrap; display: none; font-size: 0.9em; }
            .result.error { background: #f8d7da; color: #721c24; }
            .result.success { background: #d4edda; color: #155724; }
            .alert { padding: 12px 16px; border-radius: 6px; margin: 10px 0; font-weight: 500; }
            .alert-warning { background: #fff3cd; color: #856404; border: 1px solid #ffc107; }
            .alert-info { background: #d1ecf1; color: #0c5460; border: 1px solid #17a2b8; }
            .summary { display: flex; gap: 20px; flex-wrap: wrap; }
            .summary .stat { text-align: center; padding: 16px 24px; background: #16213e;
                             color: #fff; border-radius: 8px; min-width: 120px; }
            .summary .stat .num { font-size: 2em; font-weight: 700; }
            .summary .stat .label { font-size: 0.85em; opacity: 0.8; }
            """;

    static final String NAV = """
            <nav>
                <a href="/" id="nav-home">Dashboard</a>
                <a href="/dba" id="nav-dba">DBA Console</a>
                <a href="/migrator" id="nav-migrator">Migrator</a>
                <a href="/cicd" id="nav-cicd">CI/CD</a>
                <a href="/explorer" id="nav-explorer">Explorer</a>
                <a href="/wizard" id="nav-wizard">Setup Wizard</a>
                <a href="/sample-app" id="nav-sample">Sample App</a>
            </nav>
            """;

    static final String SCRIPT_UTILS = """
            function showResult(id, data, isError) {
                var el = document.getElementById(id);
                el.style.display = 'block';
                el.className = 'result ' + (isError ? 'error' : 'success');
                el.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
            }
            function apiCall(method, url, body) {
                var opts = { method: method, headers: {'Content-Type': 'application/json'} };
                if (body) opts.body = JSON.stringify(body);
                return fetch(url, opts).then(function(r) { return r.json(); });
            }
            document.querySelectorAll('.masked').forEach(function(el) {
                el.addEventListener('click', function() { this.classList.toggle('revealed'); });
            });
            """;

    static String page(String title, String activeNav, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s - ConjurAdmin</title>
                    <style>%s</style>
                </head>
                <body>
                    <h1>Conjur Admin Console</h1>
                    %s
                    %s
                    <script>
                    var nav = document.getElementById('%s');
                    if (nav) nav.className = 'active';
                    </script>
                </body>
                </html>
                """.formatted(esc(title), CSS, NAV, body, activeNav);
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
