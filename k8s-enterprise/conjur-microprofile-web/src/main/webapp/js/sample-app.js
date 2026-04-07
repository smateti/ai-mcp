var saRefData = {};
var saExisting = { orgs: [], envs: {}, prods: {}, resources: {}, resTypes: {} };
var saOrgName = 'nimbus';

function saInit() {
    fetch('/api/refdata/all').then(function(r){return r.json();})
    .then(function(data){ saRefData = data; })
    .catch(function(e){ console.error(e); });

    Promise.all([
        fetch('/api/resources?kind=policy').then(function(r){return r.json();}).catch(function(){return [];}),
        fetch('/api/resources?kind=variable').then(function(r){return r.json();}).catch(function(){return [];})
    ]).then(function(results){
        parsePolicies(results[0]||[]);
        parseVariables(results[1]||[]);
        populateEnvs();
    });
}

function parsePolicies(policies) {
    policies.forEach(function(r){
        var p = (r.id||'').split(':').pop();
        var o = p.split('/')[0];
        if(o && o!=='conjur'){
            if(saExisting.orgs.indexOf(o)===-1) saExisting.orgs.push(o);
            saOrgName = saExisting.orgs[0] || 'nimbus';
        }
        var me = p.match(/^([^\/]+)\/([^\/]+)\/products/);
        if(me){
            if(!saExisting.envs[me[1]]) saExisting.envs[me[1]] = [];
            if(saExisting.envs[me[1]].indexOf(me[2])===-1) saExisting.envs[me[1]].push(me[2]);
        }
        var mp = p.match(/^([^\/]+)\/([^\/]+)\/products\/([^\/]+)$/);
        if(mp){
            var k = mp[1]+'/'+mp[2];
            if(!saExisting.prods[k]) saExisting.prods[k] = [];
            if(saExisting.prods[k].indexOf(mp[3])===-1) saExisting.prods[k].push(mp[3]);
        }
        var mr = p.match(/^([^\/]+)\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)$/);
        if(mr){
            var rk = mr[1]+'/'+mr[2]+'/'+mr[3];
            if(!saExisting.resTypes[rk]) saExisting.resTypes[rk] = [];
            if(saExisting.resTypes[rk].indexOf(mr[4])===-1) saExisting.resTypes[rk].push(mr[4]);
        }
    });
}

function parseVariables(variables) {
    var seen = {};
    variables.forEach(function(v){
        var p = (v.id||'').split(':').pop();
        var m = p.match(/^([^\/]+)\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)\/([^\/]+)\//);
        if(m){
            var k = m[1]+'/'+m[2]+'/'+m[3];
            var rk = k+'/'+m[4]+'/'+m[5];
            if(!seen[rk]){
                seen[rk] = true;
                if(!saExisting.resources[k]) saExisting.resources[k] = [];
                saExisting.resources[k].push({ type: m[4], name: m[5] });
            }
        }
    });
}

function populateEnvs() {
    var sel = document.getElementById('sa-env');
    sel.innerHTML = '';
    var envs = saExisting.envs[saOrgName] || [];
    envs.forEach(function(e){
        sel.innerHTML += '<option value="'+esc(e)+'">'+esc(e)+'</option>';
    });
    onSaEnvChange();
}

function onSaEnvChange() {
    var env = document.getElementById('sa-env').value;
    var sel = document.getElementById('sa-product');
    sel.innerHTML = '';
    var key = saOrgName + '/' + env;
    var prods = saExisting.prods[key] || [];
    prods.forEach(function(p){
        sel.innerHTML += '<option value="'+esc(p)+'">'+esc(p)+'</option>';
    });
    onSaProductChange();
}

function onSaProductChange() {
    var env = document.getElementById('sa-env').value;
    var prod = document.getElementById('sa-product').value;
    if(!env || !prod) return;
    var pk = saOrgName + '/' + env + '/' + prod;
    var resources = saExisting.resources[pk] || [];

    var lines = [];
    resources.forEach(function(r){
        var fields = getFieldsForType(r.type);
        var basePath = saOrgName+'/'+env+'/products/'+prod+'/resources/'+r.type+'/'+r.name;
        fields.forEach(function(f){
            lines.push('db.'+f+'='+basePath+'/'+f);
        });
    });
    document.getElementById('sa-mappings').value = lines.join('\n');
}

function getFieldsForType(t) {
    if(saRefData.resourcetypes) {
        var m = saRefData.resourcetypes.find(function(rt){ return rt.name === t; });
        if(m && m.fields) return m.fields.split(',').map(function(f){return f.trim();}).filter(function(f){return f;});
    }
    var d = {
        'dbs':['host-name','username','password','port','database-name'],
        'kafka':['bootstrap-servers','sasl-username','sasl-password'],
        'api':['key','secret','webhook-secret'],
        'smtp':['host','port','username','password'],
        'ldap':['url','bind-dn','bind-password'],
        'oauth':['client-id','client-secret','token-url'],
        'certs':['cert','key','ca-bundle']
    };
    return d[t] || ['value'];
}

function esc(s) {
    if(!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function saResult(msg, type) {
    var el = document.getElementById('sa-result');
    el.style.display = 'block';
    el.className = 'sa-status ' + type;
    el.textContent = typeof msg === 'string' ? msg : JSON.stringify(msg, null, 2);
}

function doDeploy() {
    saResult('Deploying...', 'info');
    var mappings = document.getElementById('sa-mappings').value.trim().split('\n').filter(function(l){return l.trim();}).join(',');
    var body = {
        appName: document.getElementById('sa-appName').value.trim(),
        namespace: document.getElementById('sa-namespace').value.trim(),
        nodePort: parseInt(document.getElementById('sa-nodePort').value),
        jwtServiceId: document.getElementById('sa-jwtServiceId').value.trim(),
        secretMappings: mappings
    };
    fetch('/api/sample-app/deploy', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body)
    }).then(function(r){return r.json();}).then(function(data){
        if(data.error) saResult(data.error, 'err');
        else saResult('Deployed!\n\nApp: '+data.service+'\nNamespace: '+data.namespace+'\nURL: '+data.url+'\n\nUse "Check Status" to monitor readiness.', 'ok');
    }).catch(function(e){ saResult('Error: '+e.message, 'err'); });
}

function doUndeploy() {
    if(!confirm('Undeploy ' + document.getElementById('sa-appName').value + '?')) return;
    saResult('Undeploying...', 'info');
    var name = document.getElementById('sa-appName').value.trim();
    var ns = document.getElementById('sa-namespace').value.trim();
    fetch('/api/sample-app/undeploy/'+encodeURIComponent(name)+'?namespace='+encodeURIComponent(ns), {
        method: 'DELETE'
    }).then(function(r){return r.json();}).then(function(data){
        if(data.error) saResult(data.error, 'err');
        else saResult(JSON.stringify(data, null, 2), 'ok');
        document.getElementById('sa-pods').innerHTML = '';
    }).catch(function(e){ saResult('Error: '+e.message, 'err'); });
}

function doStatus() {
    var name = document.getElementById('sa-appName').value.trim();
    var ns = document.getElementById('sa-namespace').value.trim();
    fetch('/api/sample-app/status/'+encodeURIComponent(name)+'?namespace='+encodeURIComponent(ns))
    .then(function(r){return r.json();}).then(function(data){
        if(data.error){ saResult(data.error, 'err'); return; }
        if(!data.deployed){ saResult('Not deployed: '+name, 'info'); document.getElementById('sa-pods').innerHTML=''; return; }

        var ready = (data.readyReplicas||0) + '/' + (data.replicas||0);
        saResult('Deployment: '+name+'\nReady: '+ready+'\nNodePort: '+(data.nodePort||'n/a')+'\nURL: http://localhost:'+(data.nodePort||'?'), 'ok');

        var html = '';
        (data.pods||[]).forEach(function(pod){
            html += '<div class="sa-pod"><h4>Pod: '+esc(pod.name)+' <span class="badge '+(pod.phase==='Running'?'badge-ok':'badge-warn')+'">'+esc(pod.phase)+'</span></h4>';
            if(pod.initContainer){
                var ic = pod.initContainer;
                html += '<p><strong>Init:</strong> '+esc(ic.name)+' - <span class="badge '+(ic.status==='terminated' && ic.exitCode===0?'badge-ok':'badge-warn')+'">'+esc(ic.status)+'</span>';
                if(ic.exitCode !== undefined) html += ' (exit: '+ic.exitCode+')';
                if(ic.reason) html += ' '+esc(ic.reason);
                html += '</p>';
            }
            if(pod.container){
                var c = pod.container;
                html += '<p><strong>App:</strong> '+esc(c.name)+' - <span class="badge '+(c.ready?'badge-ok':'badge-warn')+'">'+esc(c.status)+'</span>';
                if(c.restartCount > 0) html += ' (restarts: '+c.restartCount+')';
                if(c.reason) html += ' '+esc(c.reason);
                html += '</p>';
            }
            html += '</div>';
        });
        document.getElementById('sa-pods').innerHTML = html;
    }).catch(function(e){ saResult('Error: '+e.message, 'err'); });
}

function doLogs(container) {
    saResult('Fetching logs...', 'info');
    var name = document.getElementById('sa-appName').value.trim();
    var ns = document.getElementById('sa-namespace').value.trim();
    fetch('/api/sample-app/logs/'+encodeURIComponent(name)+'?namespace='+encodeURIComponent(ns)+'&container='+encodeURIComponent(container))
    .then(function(r){return r.json();}).then(function(data){
        if(data.error) saResult(data.error, 'err');
        else saResult('=== '+data.container+' logs ('+data.pod+') ===\n\n'+data.log, 'info');
    }).catch(function(e){ saResult('Error: '+e.message, 'err'); });
}

saInit();
