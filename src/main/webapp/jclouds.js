window.JClouds = window.JClouds || {
    "showdlg": function showdlg(url, title, postfunc) {
        fetch(url).then((response) => {
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.text();
        }).then((txt) => {
            window.JClouds.body = document.createElement('div');
            window.JClouds.body.innerHTML = txt;
            if (null != postfunc) {
                postfunc();
            }
            dialog.modal(window.JClouds.body, {
                minWidth: window.innerWidth * 3 / 4 + "px",
                maxWidth: window.innerWidth * 3 / 4 + "px",
                title: title || "Details",
            });
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
    "showcf": function(evt) {
        var but = evt.target || evt.srcElement;
        var fid = but.closest('div').parentElement.querySelector('select').value;
        evt.preventDefault(); evt.stopPropagation();
        window.JClouds.showdlg(rootURL + '/configfiles/show?id=' + fid,
                               but.dataset.dlgtitle, window.JClouds.showcfpost);
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
