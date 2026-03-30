package com.example.propset.rest;

import com.example.propset.service.ConfigMapPublisher;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@RequestScoped
public class RootResource {

    @Inject
    private ConfigMapPublisher publisher;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index() {
        int dsCount = publisher.getDatasources().size();
        var status = publisher.getPublishStatus();

        StringBuilder statusRows = new StringBuilder();
        status.forEach((key, value) -> statusRows.append(
                "<tr><td>" + key + "</td><td>" + value + "</td></tr>"));

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>PropSetApp</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                               max-width: 800px; margin: 40px auto; padding: 0 20px;
                               background: #f5f5f5; color: #333; }
                        h1 { color: #1a1a2e; border-bottom: 2px solid #16213e; padding-bottom: 10px; }
                        .card { background: #fff; border-radius: 8px; padding: 20px; margin: 16px 0;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .card h2 { margin-top: 0; color: #16213e; }
                        table { border-collapse: collapse; width: 100%%; }
                        th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid #eee; }
                        th { background: #f0f0f0; font-weight: 600; }
                        a { color: #0f3460; }
                        .badge { display: inline-block; padding: 2px 10px; border-radius: 12px;
                                 font-size: 0.85em; font-weight: 600; }
                        .badge-ok { background: #d4edda; color: #155724; }
                        .endpoints a { display: block; padding: 6px 0; }
                    </style>
                </head>
                <body>
                    <h1>PropSetApp</h1>
                    <p>Kubernetes ConfigMap Publisher for Datasource Configuration</p>

                    <div class="card">
                        <h2>Status</h2>
                        <p>Datasources configured: <strong>%d</strong></p>
                        <table>
                            <tr><th>ConfigMap</th><th>Status</th></tr>
                            %s
                        </table>
                    </div>

                    <div class="card endpoints">
                        <h2>Endpoints</h2>
                        <a href="/propset/status">GET /propset/status</a>
                        <a href="/propset/refresh" onclick="fetch('/propset/refresh',{method:'POST'}).then(r=>r.json()).then(d=>{alert(JSON.stringify(d,null,2));location.reload()});return false;">POST /propset/refresh</a>
                        <a href="/health">GET /health</a>
                        <a href="/health/live">GET /health/live</a>
                        <a href="/health/ready">GET /health/ready</a>
                    </div>
                </body>
                </html>
                """.formatted(dsCount, statusRows.toString());
    }
}
