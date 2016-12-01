window.JClouds = window.JClouds || {
    "showcf": function(evt) {
        var but = evt.target || evt.srcElement;
        var fid = $(but).up('tr').down('select').getValue();
        window.open(rootURL + '/configfiles/show?id=' + fid, 'window', 'width=900,height=640,resizable,scrollbars');
    },
    "managecf": function(evt) {
        window.open(rootURL + '/configfiles', 'window', 'width=900,height=640,resizable,scrollbars');
    },
    "scan": function() {
        //$$('.jclouds-showcf').each(function (e) {
        //    Behaviour.applySubtree(e, true);
        //});
        $$('.jclouds-managecf').each(function (e) {
            Behaviour.applySubtree(e, true);
        });
    }
};
// YUI-Buttons are broken in that their event handlers are not duplicated properly
// if they are inside a repeatable chunk. Normal buttons work like a charm.
//Behaviour.specify('INPUT.jclouds-showcf', 'jclouds.showcf', 0, function (e) {
//    makeButton(e, window.JClouds.showcf);
//    e = null;
//});
Behaviour.specify('INPUT.jclouds-managecf', 'jclouds.managecf', 0, function (e) {
    makeButton(e, window.JClouds.managecf);
    e = null;
}); 
window.setTimeout(window.JClouds.scan, 50);
