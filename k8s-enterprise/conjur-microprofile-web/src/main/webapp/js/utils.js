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
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.masked').forEach(function(el) {
        el.addEventListener('click', function() { this.classList.toggle('revealed'); });
    });
});

/**
 * Loads live context from Conjur by parsing actual policies, hosts, and variables.
 * Returns { orgs, envs, prods, appTypes, resTypes, resources, hosts } via callback.
 */
function loadLiveContext(callback) {
    Promise.all([
        fetch('/api/resources?kind=policy').then(function(r){return r.json();}).catch(function(){return [];}),
        fetch('/api/resources?kind=host').then(function(r){return r.json();}).catch(function(){return [];}),
        fetch('/api/resources?kind=variable').then(function(r){return r.json();}).catch(function(){return [];})
    ]).then(function(results) {
        var ctx = { orgs: [], envs: {}, prods: {}, appTypes: {}, resTypes: {}, resources: {}, hosts: {} };
        var policies = results[0] || [];
        var hosts = results[1] || [];
        var variables = results[2] || [];

        var os = {};
        policies.forEach(function(r) {
            var p = (r.id||'').split(':').pop();
            var f = p.split('/')[0];
            if(f && f!=='conjur') os[f] = true;
            var me = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products/);
            if(me){
                if(!ctx.envs[me[1]]) ctx.envs[me[1]] = [];
                if(ctx.envs[me[1]].indexOf(me[2])===-1) ctx.envs[me[1]].push(me[2]);
            }
            var mp = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)$/);
            if(mp){
                var k = mp[1]+'/'+mp[2];
                if(!ctx.prods[k]) ctx.prods[k] = [];
                if(ctx.prods[k].indexOf(mp[3])===-1) ctx.prods[k].push(mp[3]);
            }
            var ma = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/apps\/([^\/]+)$/);
            if(ma){
                var ak = ma[1]+'/'+ma[2]+'/'+ma[3];
                if(!ctx.appTypes[ak]) ctx.appTypes[ak] = [];
                if(ctx.appTypes[ak].indexOf(ma[4])===-1) ctx.appTypes[ak].push(ma[4]);
            }
            var mr = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)$/);
            if(mr){
                var rk = mr[1]+'/'+mr[2]+'/'+mr[3];
                if(!ctx.resTypes[rk]) ctx.resTypes[rk] = [];
                if(ctx.resTypes[rk].indexOf(mr[4])===-1) ctx.resTypes[rk].push(mr[4]);
            }
        });
        ctx.orgs = Object.keys(os).sort();

        var seen = {};
        variables.forEach(function(v){
            var p = (v.id||'').split(':').pop();
            var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/resources\/([^\/]+)\/([^\/]+)\//);
            if(m){
                var k = m[1]+'/'+m[2]+'/'+m[3];
                var rk = k+'/'+m[4]+'/'+m[5];
                if(!seen[rk]){
                    seen[rk] = true;
                    if(!ctx.resources[k]) ctx.resources[k] = [];
                    ctx.resources[k].push({ type: m[4], name: m[5] });
                }
            }
        });

        var hSeen = {};
        hosts.forEach(function(h){
            var p = (h.id||'').split(':').pop();
            var m = p.match(/^([^\/]+)\/environments\/([^\/]+)\/products\/([^\/]+)\/apps\/([^\/]+)\/(.+)/);
            if(m){
                var k = m[1]+'/'+m[2]+'/'+m[3];
                var hk = k+'/'+m[4]+'/'+m[5];
                if(!hSeen[hk]){
                    hSeen[hk] = true;
                    if(!ctx.hosts[k]) ctx.hosts[k] = [];
                    ctx.hosts[k].push({ appType: m[4], hostId: m[5] });
                }
            }
        });

        callback(ctx);
    }).catch(function(e) {
        console.error('loadLiveContext error:', e);
        callback({ orgs: [], envs: {}, prods: {}, appTypes: {}, resTypes: {}, resources: {}, hosts: {} });
    });
}

function fillLiveSelect(id, items, placeholder) {
    var sel = document.getElementById(id);
    if(!sel) return;
    sel.innerHTML = '<option value="">'+(placeholder||'-- Select --')+'</option>';
    items.forEach(function(i){ sel.innerHTML += '<option value="'+escHtml(i)+'">'+escHtml(i)+'</option>'; });
}

function escHtml(s) {
    if(!s) return '';
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
