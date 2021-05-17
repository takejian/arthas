var ws;
var xterm;

$(function () {
    var url = window.location.href;
    var ip = getUrlParam('ip');
    var port = getUrlParam('port');
    var host = getUrlParam('host');
    var appName = getUrlParam('appName');

    if (ip != '' && ip != null) {
        $('#ip').val(ip);
    } else {
        $('#ip').val(window.location.hostname);
    }
    if (port != '' && port != null) {
        $('#port').val(port);
    }
    if (host != '' && host != null) {
        $('#host').val(host);
    }
    if (appName != '' && appName != null) {
        $('#appName').val(appName);
    }

    startConnect(true);
});

/** get params in url **/
function getUrlParam (name, url) {
    if (!url) url = window.location.href;
    name = name.replace(/[\[\]]/g, '\\$&');
    var regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, ' '));
}

function getCharSize () {
    var tempDiv = $('<div />').attr({'role': 'listitem'});
    var tempSpan = $('<div />').html('qwertyuiopasdfghjklzxcvbnm');
    tempDiv.append(tempSpan);
    $("html body").append(tempDiv);
    var size = {
        width: tempSpan.outerWidth() / 26,
        height: tempSpan.outerHeight(),
        left: tempDiv.outerWidth() - tempSpan.outerWidth(),
        top: tempDiv.outerHeight() - tempSpan.outerHeight(),
    };
    tempDiv.remove();
    return size;
}

function getWindowSize () {
    var e = window;
    var a = 'inner';
    if (!('innerWidth' in window )) {
        a = 'client';
        e = document.documentElement || document.body;
    }
    var terminalDiv = document.getElementById("terminal-card");
    var terminalDivRect = terminalDiv.getBoundingClientRect();
    return {
        width: terminalDivRect.width,
        height: e[a + 'Height'] - terminalDivRect.top
    };
}

function getTerminalSize () {
    var charSize = getCharSize();
    var windowSize = getWindowSize();
    console.log('charsize');
    console.log(charSize);
    console.log('windowSize');
    console.log(windowSize);
    return {
        cols: Math.floor((windowSize.width - charSize.left) / 10),
        rows: Math.floor((windowSize.height - charSize.top) / 17)
    };
}

/** init websocket **/
function initWs (ip, port, path, host, appName, targetServer) {
    var protocol= location.protocol === 'https:'  ? 'wss://' : 'ws://';
    var uri = protocol + ip + ':' + port + '/' + encodeURIComponent(path) + '?method=connectArthas&host=' +host +'&appName=' + appName;
    if (targetServer != null) {
        uri = uri + '&targetServer=' + encodeURIComponent(targetServer);
    }
    ws = new WebSocket(uri);
}

/** init xterm **/
function initXterm (cols, rows) {
    xterm = new Terminal({
        cols: cols,
        rows: rows,
        screenReaderMode: true,
        rendererType: 'canvas',
        convertEol: true
    });
}

/** begin connect **/
function startConnect (silent) {
    var ip = $('#ip').val();
    var port = $('#port').val();
    var host = $('#host').val();
    var appName = $('#appName').val();
    if (ip == '' || port == '') {
        alert('Ip or port can not be empty');
        return;
    }
    if (appName == '') {
        if (silent) {
            return;
        }
        alert('appName can not be empty');
        return;
    }
    if (ws != null) {
        alert('Already connected');
        return;
    }
    
    var path = getUrlParam('path');
    if (path == null) {
        path = "ws";
    }

    var targetServer = getUrlParam('targetServer');

    // init webSocket
    initWs(ip, port, path, host, appName, targetServer);
    ws.onerror = function () {
        ws.close();
        ws = null;
        !silent && alert('Connect error');
    };
    ws.onclose = function (message) {
        if (message.code === 2000) {
            alert(message.reason);
        }
    };
    ws.onopen = function () {
        console.log('open');
        $('#fullSc').show();
        var terminalSize = getTerminalSize()
        console.log('terminalSize')
        console.log(terminalSize)
        // init xterm
        initXterm(terminalSize.cols, terminalSize.rows)
        ws.onmessage = function (event) {
            if (event.type === 'message') {
                var data = event.data;
                xterm.write(data);
            }
        };
        xterm.open(document.getElementById('terminal'));
        xterm.on('data', function (data) {
            ws.send(JSON.stringify({action: 'read', data: data}))
        });
        ws.send(JSON.stringify({action: 'resize', cols: terminalSize.cols, rows: terminalSize.rows}));
        window.setInterval(function () {
            if (ws != null && ws.readyState === 1) {
                ws.send(JSON.stringify({action: 'read', data: ""}));
            }
        }, 30000);
    }
}

function disconnect () {
    try {
        ws.close();
        ws.onmessage = null;
        ws.onclose = null;
        ws = null;
        xterm.destroy();
        $('#fullSc').hide();
        alert('Connection was closed successfully!');
    } catch (e) {
        alert('No connection, please start connect first.');
    }
}

function updateArthasOutputLink() {
    $('#arthasOutputA').prop("href", "proxy/" + $('#appName').val() + "/arthas-output/")
}

/** full screen show **/
function xtermFullScreen () {
    var ele = document.getElementById('terminal-card');
    requestFullScreen(ele);
}

function requestFullScreen (element) {
    var requestMethod = element.requestFullScreen || element.webkitRequestFullScreen || element.mozRequestFullScreen || element.msRequestFullScreen;
    if (requestMethod) {
        requestMethod.call(element);
    } else if (typeof window.ActiveXObject !== "undefined") {
        var wscript = new ActiveXObject("WScript.Shell");
        if (wscript !== null) {
            wscript.SendKeys("{F11}");
        }
    }
}
