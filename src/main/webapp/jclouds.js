window.JClouds = window.JClouds || {
    "initdlg": function initdlg() {
        if (!(window.JClouds.dlg)) {
            var div = document.createElement('DIV');
            document.body.appendChild(div);
            div.innerHTML = '<div id="jcloudsDialog" class="bd" style="overflow-y: scroll;"></div>';
            window.JClouds.body = document.getElementById('jcloudsDialog');
            window.JClouds.dlg = new YAHOO.widget.Panel(div, {
                fixedcenter: true,
                close: true,
                draggable: true,
                zindex: 1000,
                modal: true,
                visible: false,
                keylisteners: [
                    new YAHOO.util.KeyListener(
                        document, {"keys": 27},
                        {
                            "fn": function() {
                                window.JClouds.dlg.hide();
                                window.JClouds.body.innerHTML = '';
                            },
                            scope:document,
                            correctScope:false
                        })
                ]
            });
            window.JClouds.dlg.render();
        }
    },
    "showdlg": function showdlg(url, postfunc) {
        window.JClouds.initdlg();
        fetch(url).then((response) => {
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.text();
        }).then((txt) => {
            var r = YAHOO.util.Dom.getClientRegion();
            var dlg = window.JClouds.dlg;
            window.JClouds.body.innerHTML = txt;
            dlg.cfg.setProperty("width", r.width * 3 / 4 + "px");
            dlg.cfg.setProperty("height", r.height * 3 / 4 + "px");
            dlg.center();
            if (null != postfunc) {
                postfunc();
            }
            dlg.show();
        });
    },
    "removeIfExists": function removeIfExists(body, selector) {
        var el = body.querySelector(selector);
        if (el) {
            el.remove();
        }
    },
    "showcfpost": function showcfpost() {
        var body = window.JClouds.body;
        window.JClouds.removeIfExists(body, '#page-head');
        window.JClouds.removeIfExists(body, '#page-header');
        window.JClouds.removeIfExists(body, '#breadcrumbBar');
        window.JClouds.removeIfExists(body, '#side-panel');
        window.JClouds.removeIfExists(body, 'footer');
        Behaviour.applySubtree(body, false);
    },
    "mancfpost": function mancfpost() {
        var body = window.JClouds.body;
        window.JClouds.removeIfExists(body, '#page-head');
        window.JClouds.removeIfExists(body, '#page-header');
        window.JClouds.removeIfExists(body, '#breadcrumbBar');
        window.JClouds.removeIfExists(body, '#side-panel');
        window.JClouds.removeIfExists(body, 'footer');
        Behaviour.applySubtree(body, false);
    },
    "showcf": function(evt) {
        var but = evt.target || evt.srcElement;
        var fid = but.closest('div').parentElement.querySelector('select').value;
        evt.preventDefault(); evt.stopPropagation();
        window.JClouds.showdlg(rootURL + '/configfiles/show?id=' + fid, window.JClouds.showcfpost);
    },
    "chgbutton": function(sel) {
        var dis = sel.value == '';
        var but = sel.parentElement.parentElement.nextSibling.querySelector('.jclouds-showcf');
        if (but) {
            if (dis) {
                but.setAttribute('disabled', "");
            } else {
                but.removeAttribute('disabled');
            }
        }
    },
    "chsel": function(evt) {
        var sel = evt.target || evt.srcElement;
        evt.preventDefault(); evt.stopPropagation();
        window.JClouds.chgbutton(sel);
    },
    "managecf": function(evt) {
        evt.preventDefault(); evt.stopPropagation();
        window.open(rootURL + '/configfiles', 'window', 'width=900,height=640,resizable,scrollbars');
    }
};
Behaviour.specify('BUTTON.jclouds-showcf', 'jclouds', 99, function (e) {
    e.onclick = window.JClouds.showcf;
});
Behaviour.specify('BUTTON.jclouds-managecf', 'jclouds', 99, function (e) {
    e.onclick = window.JClouds.managecf;
});
Behaviour.specify('SELECT.jclouds', 'jclouds', 101, function (e) {
    e.removeEventListener('change', window.JClouds.chsel);
    e.addEventListener('change', window.JClouds.chsel);
});
