'use strict';
const R = 6371008.8; // mean Earth radius, meters
const rad = (d) => (d * Math.PI) / 180;

/** Great-circle distance in meters between two {lat,lng} points. */
function haversine(a, b) {
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}
module.exports = { haversine };
