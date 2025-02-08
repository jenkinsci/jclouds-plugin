Behaviour.specify("[data-type='jclouds-provision']", 'jclouds-provision', -99, function(e) {
  e.addEventListener("click", function (event) {
    const form = document.getElementById(e.dataset.form);
    form.querySelector("[name='tplname']").value = e.dataset.url;
    buildFormTree(form);
    form.submit();
  });
});
