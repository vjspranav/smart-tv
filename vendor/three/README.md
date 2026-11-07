three.js 0.160.0 for the house view (house.js), from cdn.jsdelivr.net/npm/three@0.160.0: build/three.module.min.js and
the examples/jsm add-ons used. One local change: add-ons import `/vendor/three/three.module.min.js` instead of the bare
`three`, because the page's Content-Security-Policy blocks the inline import map that would otherwise map it.
