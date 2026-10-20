(function () {
  var options = {};
  try {
    options = JSON.parse(decodeURIComponent(location.hash.slice(1) || '%7B%7D'));
  } catch (e) { /* defaults below */ }

  var QUOTE_MS = 9000;
  var QUOTES = [
    "THE CAKE IS A LIE.",
    "STILL ALIVE.",
    "THIS WAS A TRIUMPH.",
    "I'M MAKING A NOTE HERE: HUGE SUCCESS.",
    "WE DO WHAT WE MUST BECAUSE WE CAN.",
    "THE ENRICHMENT CENTER IS MANDATORY.",
    "SCIENCE ISN'T ABOUT WHY, IT'S ABOUT WHY NOT.",
    "FOR SCIENCE. YOU MONSTER.",
    "IT'S BEEN A LONG TIME. HOW HAVE YOU BEEN?",
    "I HAVE AN IDEA. LET'S TEST IT ON YOU.",
    "EVERYTHING IS FINE. NOTHING IS ON FIRE.",
    "PLEASE REMAIN CALM. I AM CALMLY TESTING THINGS."
  ];
  var showQuote = options.quotes !== false;
  var quoteIndex = Math.floor(Math.random() * QUOTES.length);
  var lastQuoteChange = 0;
  var quoteEl = document.getElementById('quote');
  var eye = document.getElementById('eye');
  var glow = document.createElement('div');
  var core = document.createElement('div');
  var pupil = document.createElement('div');
  glow.id = 'glow'; core.id = 'core'; pupil.id = 'pupil';
  eye.appendChild(glow); eye.appendChild(core); eye.appendChild(pupil);

  function size() {
    // The lens in the picture: radius 5.8% of the scene, ring 14.5%.
    var scene = document.getElementById('scene');
    var side = scene.getBoundingClientRect().width;
    var coreR = side * 0.052;
    function set(el, r) {
      el.style.width = (r * 2) + 'px';
      el.style.height = (r * 2) + 'px';
    }
    set(core, coreR * 1.6);
    set(glow, side * 0.145 * 0.92);
    set(pupil, coreR * 0.62);
  }
  window.addEventListener('resize', size);
  size();

  var t0 = performance.now();
  function frame(now) {
    var t = (now - t0) / 1000;
    // The glow breathes at rest and flares gently on its own, like a turn.
    var flare = 0.3 + 0.18 * Math.sin(t * 0.9) + 0.1 * Math.sin(t * 2.7);
    glow.style.background = 'radial-gradient(circle, rgba(255,193,65,' +
      (0.55 * flare).toFixed(3) + ') 0%, rgba(255,193,65,' + (0.22 * flare).toFixed(3) +
      ') 45%, rgba(255,193,65,0) 100%)';
    var breath = 0.5 + 0.5 * Math.sin(t * 1.7);
    pupil.style.opacity = (0.55 + 0.2 * flare + 0.15 * breath).toFixed(3);
    if (showQuote) {
      if (now - lastQuoteChange > QUOTE_MS) {
        lastQuoteChange = now;
        quoteEl.style.opacity = 0;
        setTimeout(function () {
          quoteEl.textContent = QUOTES[quoteIndex % QUOTES.length];
          quoteIndex++;
          quoteEl.style.opacity = 1;
        }, 1200);
      }
    } else {
      quoteEl.style.opacity = 0;
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
})();
