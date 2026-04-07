// Dashboard & Databases page JS - masked value toggle on dynamically loaded content
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.masked').forEach(function(el) {
        el.addEventListener('click', function() { this.classList.toggle('revealed'); });
    });
});
