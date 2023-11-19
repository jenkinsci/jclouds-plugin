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
    "showcfpost": function showcfpost() {
        var body = window.JClouds.body;
        var phead = body.querySelector('#page-head') || body.querySelector('#page-header');
        if (phead) {
            phead.remove();
        }
        var bcb = body.querySelector('#breadcrumbBar');
        if (bcb) {
            bcb.remove();
        }
        body.querySelector('#side-panel').remove();
        body.querySelector('footer').remove();
        Behaviour.applySubtree(body, false);
    },
    "mancfpost": function mancfpost() {
        var body = window.JClouds.body;
        var phead = body.querySelector('#page-head') || body.querySelector('#page-header');
        if (phead) {
            phead.remove();
        }
        var bcb = body.querySelector('#breadcrumbBar');
        if (bcb) {
            bcb.remove();
        }
        body.querySelector('#side-panel').remove();
        body.querySelector('footer').remove();
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
        var but = sel.closest('div').parentElement.querySelector('.yui-button') || sel.closest('div').parentElement.parentElement.querySelector('.yui-button');
        if (but) {
            but.yb.set('disabled', dis, true);
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
Behaviour.specify('INPUT.jclouds-showcf', 'jclouds', 99, function (e) {
    // Jenkins' makeButton is complete crap when it comes to event handling
    // inside a repeatable chunk.
    // Therefore, we specify a null event handler here and let the
    // SPAN... behavior install EXACTLY ONE click handler on the resulting
    // YUI button. (An YUI button's outermost element is a SPAN.)
    var b = makeButton(e, null);
    // Attach the YUI button object to the DOM element, so we can retrieve
    // it later (for disabling/enabling the button) from within our onchange
    // event handler for the selects.
    b.get("element").yb =  b;
    e = null;
});
Behaviour.specify('INPUT.jclouds-managecf', 'jclouds', 99, function (e) {
    var b = makeButton(e, null);
    e = null;
}); 
Behaviour.specify('SPAN.jclouds-showcf', 'jclouds', 100, function (e) {
    e.removeEventListener('click', window.JClouds.showcf);
    e.addEventListener('click', window.JClouds.showcf);
});
Behaviour.specify('SPAN.jclouds-managecf', 'jclouds', 100, function (e) {
    e.removeEventListener('click', window.JClouds.managecf);
    e.addEventListener('click', window.JClouds.managecf);
});
Behaviour.specify('SELECT.jclouds', 'jclouds', 101, function (e) {
    e.removeEventListener('change', window.JClouds.chsel);
    e.addEventListener('change', window.JClouds.chsel);
});
