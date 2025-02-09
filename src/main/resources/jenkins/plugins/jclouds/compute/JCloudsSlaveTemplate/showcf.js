Behaviour.specify('BUTTON.jclouds-showcf', 'jclouds', 99, function (e) {
    e.onclick = window.JClouds.showcf;
});
Behaviour.specify('SELECT.jclouds', 'jclouds', 101, function (e) {
    e.removeEventListener('change', window.JClouds.chsel);
    e.addEventListener('change', window.JClouds.chsel);
});
