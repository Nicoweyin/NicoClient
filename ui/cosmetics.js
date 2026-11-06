'use strict';
// The Cosmetics tab. The painting matches Cosmetics.java in the mod, so what you pick here is what the game wears.

const COSMETICS = {
  cape: ['Off', 'Nebula', 'Flame', 'Ocean', 'Nico', 'Aurora', 'Sakura', 'Rainbow', 'Ice', 'Gold', 'Carbon', 'Lava'],
  wings: ['Off', 'Angel', 'Dragon', 'Neon', 'Butterfly', 'Phoenix', 'Crystal', 'Bat'],
  halo: ['Off', 'Gold', 'Accent', 'Rainbow', 'Ice'],
  trail: ['Off', 'Hearts', 'Sparkles', 'Flames', 'Notes', 'Snow']
};
const TRAIL_SYMBOLS = ['', '♥', '✦', '♨', '♪', '❄'];
const TRAIL_COLORS = ['', '#ff5c8a', '#fff3b0', '#ff8a2a', '#7cf29a', '#bfe9ff'];
const ACCENTS = [0xFF736AF5, 0xFF4C8DFF, 0xFF22B8CF, 0xFF34C77B, 0xFFEC5FA6];
const WHITE = 0xFFFFFFFF;

// ---- Color helpers, the same math as Ui.java (ARGB integers).
function mix(from, to, t) {
  t = Math.max(0, Math.min(1, t));
  let result = 0;
  for (let shift = 0; shift <= 24; shift += 8) {
    const a = (from >>> shift) & 255, b = (to >>> shift) & 255;
    result |= Math.round(a + (b - a) * t) << shift;
  }
  return result >>> 0;
}
function alpha(color, a) { return ((Math.round((color >>> 24) * Math.max(0, Math.min(1, a))) << 24) | (color & 0xFFFFFF)) >>> 0; }
function hue(h, s, b) {
  h = (h - Math.floor(h)) * 6;
  const i = Math.floor(h), f = h - i, p = b * (1 - s), q = b * (1 - s * f), t = b * (1 - s * (1 - f));
  const [r, g, bl] = [[b, t, p], [q, b, p], [p, b, t], [p, q, b], [t, p, b], [b, p, q]][i % 6];
  return (0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(bl * 255)) >>> 0;
}
function hash(x, y, seed) {
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(seed, 2147483647)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return (h ^ (h >>> 16)) & 0x7FFFFFFF;
}

function capePixel(style, x, y, accent) {
  const face = y >= 1 && y < 17 && ((x >= 1 && x < 11) || (x >= 12 && x < 22));
  const fx = x >= 12 ? x - 12 : x - 1, fy = y - 1;
  const v = Math.max(0, Math.min(1, fy / 15));
  switch (style) {
    case 1: {
      const cloud = 0.5 + 0.5 * Math.sin(fx * 0.9 + fy * 0.45) * Math.cos(fy * 0.3 - fx * 0.2);
      const color = mix(mix(0xFF140F3A, 0xFF3B1A6E, v), 0xFFB04BD8, cloud * 0.35);
      return hash(x, y, 7) % 23 === 0 ? mix(color, WHITE, 0.85) : color;
    }
    case 2: {
      const flame = v + 0.18 * Math.sin(fx * 1.3 + fy * 0.2) + (hash(x, y, 3) % 100) / 900;
      return flame < 0.45 ? mix(0xFF1E0804, 0xFF7A1A08, flame / 0.45)
        : flame < 0.8 ? mix(0xFF7A1A08, 0xFFFF6A14, (flame - 0.45) / 0.35)
        : mix(0xFFFF6A14, 0xFFFFD25A, Math.min(1, (flame - 0.8) / 0.3));
    }
    case 3: {
      const wave = 0.5 + 0.5 * Math.sin(fy * 1.1 + Math.sin(fx * 0.8) * 1.6);
      const color = mix(mix(0xFF062A4A, 0xFF0B5C8A, v), 0xFF2CD3D6, wave * 0.45);
      return fy % 5 === 0 && wave > 0.8 ? mix(color, WHITE, 0.4) : color;
    }
    case 5: {
      const band = 0.5 + 0.5 * Math.sin(fx * 0.7 + fy * 0.35 + Math.sin(fy * 0.5) * 1.2);
      return mix(mix(0xFF050B1E, 0xFF0B1A3A, v), mix(0xFF2BE3A0, 0xFF9B5CF6, v), band * 0.75);
    }
    case 6: {
      const petal = hash(Math.trunc(x / 2), Math.trunc(y / 2), 11) % 9;
      return petal === 0 ? WHITE : petal === 1 ? 0xFFE0558F : mix(0xFFFFD6E6, 0xFFF59AC0, v);
    }
    case 7: return hue(((fy / 16 + fx * 0.012) % 1 + 1) % 1, 0.75, 1);
    case 8: {
      const crack = (fx + fy * 2) % 7 === 0 || (fx * 3 - fy + 40) % 11 === 0;
      const base = mix(0xFFE4F6FF, 0xFF7CC8F0, v);
      return crack ? mix(base, WHITE, 0.8) : base;
    }
    case 9: {
      const shine = Math.max(0, 1 - Math.abs(fx + fy - 12) / 3);
      const base = (fx + fy) % 4 === 0 ? 0xFFB8860B : mix(0xFFFFD45A, 0xFFE0A21C, v);
      return mix(base, WHITE, shine * 0.6);
    }
    case 10:
      if (face && (fx === 4 || fx === 5)) return accent;
      return (Math.trunc(fx / 2) + Math.trunc(fy / 2)) % 2 === 0 ? 0xFF1A1C22 : 0xFF2A2D36;
    case 11: {
      const n = Math.sin(fx * 1.7 + Math.sin(fy * 0.9) * 2) + Math.sin(fy * 1.3 - fx * 0.6);
      const crack = Math.max(0, 1 - Math.abs(n) * 2.2);
      return mix(0xFF1C1210, mix(0xFFFF4A0A, 0xFFFFC23A, crack), crack);
    }
    default: {
      if (!face) return 0xFF0D0F1E;
      if (fx === 0 || fx === 9 || fy === 0 || fy === 15) return accent;
      const emblem = fy >= 4 && fy <= 11 && (fx === 2 || fx === 7 || (fx >= 3 && fx <= 6 && fy - 4 >= (fx - 2) * 2 - 1 && fy - 4 <= (fx - 2) * 2));
      return emblem ? mix(accent, WHITE, 0.45) : mix(0xFF0D0F1E, accent, v * 0.25);
    }
  }
}

function wingTop(style, u) { return style === 4 ? 0.08 + Math.pow(u - 0.45, 2) - 0.1 : 0.22 - 0.24 * Math.sin(u * Math.PI * 0.75); }
function wingBottom(style, u) {
  if (style === 4) return 0.92 - 0.3 * u - 0.18 * Math.sin(u * Math.PI);
  return 1 - 0.58 * u - 0.05 * Math.abs(Math.sin(u * Math.PI * 7));
}
function wingPixel(style, u, v, x, y, accent) {
  const top = wingTop(style, u), bottom = wingBottom(style, u);
  if (u > 0.98 || v < top || v > bottom) return 0;
  const depth = (v - top) / Math.max(0.01, bottom - top);
  const edge = Math.min(Math.min(v - top, bottom - v) * 63, (0.98 - u) * 63);
  switch (style) {
    case 1: {
      const color = mix(WHITE, 0xFFCFE3FF, u * 0.6 + depth * 0.3);
      const feather = depth > 0.45 && (x + Math.trunc(depth * 6)) % 5 === 0;
      return feather ? mix(color, 0xFF9DB6D8, 0.5) : edge < 1.2 ? mix(color, 0xFFAFC4E0, 0.6) : color;
    }
    case 2: {
      const bone = (Math.abs(depth * 4 - Math.round(depth * 4)) < 0.06 && depth < 0.9) || v - top < 0.035;
      const membrane = mix(0xFF2A0D3C, 0xFF8E1B2E, u * 0.7 + depth * 0.3);
      return bone ? 0xFF1A0A12 : edge < 1.2 ? mix(membrane, 0xFF140610, 0.6) : alpha(membrane, 0.93);
    }
    case 4: {
      if (edge < 1.8) return 0xFF1A1020;
      const sx = u - 0.62, sy = depth - 0.45, spot = sx * sx * 4 + sy * sy;
      if (spot < 0.012) return 0xFF1A1020;
      if (spot < 0.03) return WHITE;
      return hue(0.55 + u * 0.3 + depth * 0.1, 0.7, 1);
    }
    case 5: {
      const fire = u < 0.5 ? mix(0xFFFFE27A, 0xFFFF8A1E, u * 2) : mix(0xFFFF8A1E, 0xFFD3261A, (u - 0.5) * 2);
      const feather = depth > 0.4 && (x + Math.trunc(depth * 8)) % 4 === 0;
      return feather ? mix(fire, 0xFF7A1408, 0.45) : edge < 1.2 ? 0xFFFFF1B0 : fire;
    }
    case 6: {
      if (edge < 1.4) return WHITE;
      const facet = (x + y) % 9 === 0 || (x - y + 64) % 13 === 0;
      return facet ? alpha(0xFFE8FBFF, 0.9) : alpha(mix(0xFF9BE3FF, 0xFF4C8DFF, u), 0.55);
    }
    case 7: {
      const bone = (Math.abs(depth * 3 - Math.round(depth * 3)) < 0.05 && depth < 0.85) || v - top < 0.03;
      return bone ? 0xFF15151A : edge < 1.2 ? 0xFF202026 : mix(0xFF3A3A44, 0xFF55505E, u);
    }
    default: {
      const glow = mix(accent, 0xFF6BF2FF, u);
      if (edge < 1.6) return glow;
      const stripe = Math.abs(depth * 5 - Math.round(depth * 5)) < 0.05;
      return stripe ? alpha(glow, 0.85) : alpha(mix(0xFF0B0A1C, accent, 0.18), 0.82);
    }
  }
}

function haloPixel(style, x, y, accent) {
  const light = mix(accent, WHITE, 0.4);
  const color = style === 1 ? 0xFFFFD45A : style === 4 ? 0xFF9BE8FF : light;
  const dx = (x + 0.5) / 16 - 1, dy = (y + 0.5) / 16 - 1;
  const ring = 1 - Math.abs(Math.sqrt(dx * dx + dy * dy) - 0.78) / 0.14;
  if (ring <= 0) return 0;
  const tint = style === 3 ? hue((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1, 0.7, 1) : color;
  return alpha(mix(tint, WHITE, ring * 0.5), Math.min(1, ring * 1.6));
}

/** Paints a small texture into an offscreen canvas. */
function paint(width, height, pixel) {
  const canvas = document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d');
  const image = context.createImageData(width, height);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const c = pixel(x, y), i = (y * width + x) * 4;
    image.data[i] = (c >>> 16) & 255; image.data[i + 1] = (c >>> 8) & 255; image.data[i + 2] = c & 255; image.data[i + 3] = c >>> 24;
  }
  context.putImageData(image, 0, 0);
  return canvas;
}

const capeFace = (style, accent) => paint(10, 16, (x, y) => capePixel(style, x + 1, y + 1, accent));
const wingTexture = (style, accent) => paint(64, 64, (x, y) => wingPixel(style, x / 63, y / 63, x, y, accent));
const haloTexture = (style, accent) => paint(32, 32, (x, y) => haloPixel(style, x, y, accent));

/** The left wing is the texture as painted; the right wing is its mirror image. */
function drawWings(context, texture, cx, top, span, height) {
  context.save();
  context.translate(cx, 0);
  context.drawImage(texture, 4, top, span, height);
  context.scale(-1, 1);
  context.drawImage(texture, 4, top, span, height);
  context.restore();
}

/** The big preview: your player seen from behind, wearing everything you picked. */
function drawPreview(canvas, choice) {
  const context = canvas.getContext('2d');
  const accent = ACCENTS[choice.accent] ?? ACCENTS[0];
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.imageSmoothingEnabled = false;
  const cx = canvas.width / 2, unit = 6, head = 8 * unit, top = 46;
  const glow = context.createRadialGradient(cx, canvas.height - 34, 4, cx, canvas.height - 34, 90);
  glow.addColorStop(0, `#${(accent & 0xFFFFFF).toString(16).padStart(6, '0')}66`);
  glow.addColorStop(1, '#00000000');
  context.fillStyle = glow;
  context.fillRect(0, canvas.height - 80, canvas.width, 80);
  if (choice.wings > 0) drawWings(context, wingTexture(choice.wings, accent), cx, top + head - 18, 110, 120);
  const body = (x, y, w, h, color) => { context.fillStyle = color; context.fillRect(cx + x * unit, top + y * unit, w * unit, h * unit); };
  body(-4, 0, 8, 8, '#3a3550'); body(-4, 8, 8, 12, '#2c3150'); body(-8, 8, 4, 12, '#353a5a'); body(4, 8, 4, 12, '#353a5a');
  body(-4, 20, 4, 12, '#22263f'); body(0, 20, 4, 12, '#262b47');
  if (choice.cape > 0) context.drawImage(capeFace(choice.cape, accent), cx - 5 * unit, top + 8 * unit, 10 * unit, 16 * unit);
  if (choice.halo > 0) context.drawImage(haloTexture(choice.halo, accent), cx - 30, top - 26, 60, 22);
  if (choice.trail > 0) {
    context.font = '18px "Segoe UI Symbol", sans-serif';
    context.fillStyle = TRAIL_COLORS[choice.trail];
    [[-46, 26, 0.9], [38, 18, 0.7], [-22, 6, 0.55], [18, 38, 0.8]].forEach(([dx, dy, a]) => {
      context.globalAlpha = a;
      context.fillText(TRAIL_SYMBOLS[choice.trail], cx + dx, canvas.height - dy);
    });
    context.globalAlpha = 1;
  }
}

/** A small picture for one choice card. */
function drawThumbnail(canvas, slot, style, accent) {
  const context = canvas.getContext('2d');
  context.imageSmoothingEnabled = false;
  context.clearRect(0, 0, canvas.width, canvas.height);
  const w = canvas.width, h = canvas.height;
  if (style === 0) {
    context.strokeStyle = '#4a5072'; context.lineWidth = 2;
    context.beginPath(); context.arc(w / 2, h / 2, 13, 0, Math.PI * 2); context.moveTo(w / 2 - 9, h / 2 + 9); context.lineTo(w / 2 + 9, h / 2 - 9); context.stroke();
    return;
  }
  if (slot === 'cape') context.drawImage(capeFace(style, accent), w / 2 - 20, 4, 40, 64);
  else if (slot === 'wings') drawWings(context, wingTexture(style, accent), w / 2, 8, 46, 58);
  else if (slot === 'halo') context.drawImage(haloTexture(style, accent), w / 2 - 26, h / 2 - 26, 52, 52);
  else {
    context.font = '30px "Segoe UI Symbol", sans-serif'; context.textAlign = 'center'; context.textBaseline = 'middle';
    context.fillStyle = TRAIL_COLORS[style]; context.fillText(TRAIL_SYMBOLS[style], w / 2, h / 2 + 2);
  }
}

let choice = null;
const status = document.getElementById('cosmetics-status');

function render() {
  if (!choice) return;
  drawPreview(document.getElementById('cosmetics-preview'), choice);
  const accent = ACCENTS[choice.accent] ?? ACCENTS[0];
  for (const slot of Object.keys(COSMETICS)) {
    const grid = document.getElementById(`cosmetics-${slot}`);
    if (!grid.childElementCount) {
      COSMETICS[slot].forEach((name, style) => {
        const card = document.createElement('button');
        card.className = 'cosmetic-card';
        card.dataset.slot = slot; card.dataset.style = String(style);
        card.setAttribute('aria-label', `${slot} ${name}`);
        const canvas = document.createElement('canvas');
        canvas.width = 72; canvas.height = 72;
        const label = document.createElement('span');
        label.textContent = name;
        card.append(canvas, label);
        card.addEventListener('click', () => choose(slot, style));
        grid.append(card);
      });
    }
    grid.querySelectorAll('.cosmetic-card').forEach(card => {
      drawThumbnail(card.querySelector('canvas'), slot, Number(card.dataset.style), accent);
      const selected = Number(card.dataset.style) === choice[slot];
      card.classList.toggle('selected', selected);
      card.setAttribute('aria-pressed', String(selected));
    });
  }
  document.getElementById('cosmetics-flap').checked = choice.flap;
  const picked = Object.keys(COSMETICS).filter(slot => choice[slot] > 0).map(slot => COSMETICS[slot][choice[slot]]);
  document.getElementById('cosmetics-summary').textContent = picked.length ? picked.join(' · ') : 'Nothing equipped';
}

async function choose(slot, value) {
  try {
    choice = await window.nicoClient.setCosmetic(slot, value);
    status.textContent = 'Saved. A running game picks it up within two seconds.';
    render();
  } catch (error) {
    status.textContent = friendlyError(error, 'Could not save your cosmetics.');
  }
}

document.getElementById('cosmetics-flap').addEventListener('change', event => { void choose('flap', event.target.checked); });

async function openCosmeticsPage() {
  try {
    choice = await window.nicoClient.cosmetics();
    render();
  } catch (error) {
    status.textContent = friendlyError(error, 'Could not read your cosmetics.');
  }
}
