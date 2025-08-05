$(document).ready(function () {
  // Apply stored settings
  const isDark = localStorage.getItem('darkMode') === 'true';
  const navbarColor = localStorage.getItem('navbarColor') || 'default';

  applyDarkMode(isDark);
  applyNavbarColor(navbarColor);

  $('#darkModeToggle').prop('checked', isDark);
  $('#navbarColorSelect').val(navbarColor);

  $('#darkModeToggle').on('change', function () {
    const enabled = $(this).is(':checked');
    localStorage.setItem('darkMode', enabled);
    applyDarkMode(enabled);
  });

  $('#navbarColorSelect').on('change', function () {
    const color = $(this).val();
    localStorage.setItem('navbarColor', color);
    applyNavbarColor(color);
  });

  function applyDarkMode(enabled) {
    $('body').toggleClass('dark-mode', enabled);
  }

  function applyNavbarColor(color) {
    const $nav = $('.topnav');
    const colors = {
      blue: 'linear-gradient(to right, #4e73df, #224abe)',
      green: 'linear-gradient(to right, #1cc88a, #17a673)',
      purple: 'linear-gradient(to right, #6f42c1, #5a32a3)',
      dark: 'linear-gradient(to right, #343a40, #212529)',
      default: 'linear-gradient(to right, #4e73df, #224abe)'
    };
    $nav.css('background', colors[color] || colors.default);
  }
});
