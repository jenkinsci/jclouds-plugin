Behaviour.specify('li#jclouds', 'jclouds-fix-separator', -9999, function (e) {
    let ne = e.nextElementSibling;
    if (ne.tagName == 'LI' && !ne.classList.contains('separator')) {
        e.insertAdjacentElement('afterend', document.createElement("li")).className = 'separator';
    }
});
