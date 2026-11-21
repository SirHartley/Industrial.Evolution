varying vec2 w;

uniform vec2 uGO;
uniform float uGS;
uniform float uTime;
uniform float uCell;
uniform float uAlpha;
uniform sampler2D uTex;
uniform vec3 uHoles[48];
uniform float uHN;
uniform vec3 uC0;
uniform vec3 uC1;
uniform vec3 uC2;
uniform vec4 uSun[3];
uniform vec3 uSunC[9];
uniform float uSunN;
uniform vec4 uRing[32];
uniform vec3 uRingH[32];
uniform float uRN;

float h(vec2 p) {
	p = fract(p * vec2(123.34, 456.21));
	p += dot(p, p + 45.32);
	return fract(p.x * p.y);
}

float n(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(h(i), h(i + vec2(1.0, 0.0)), u.x), mix(h(i + vec2(0.0, 1.0)), h(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
	float s = 0.0;
	float a = 0.5;
	mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
	for (int i = 0; i < 4; i++) {
		s += a * n(p);
		p = r * p * 2.03 + 17.1;
		a *= 0.5;
	}
	return s / 0.94;
}

vec4 g(vec2 q) {
	vec2 t = (q - uGO) / uGS;
	if (t.x < 0.0 || t.y < 0.0 || t.x > 1.0 || t.y > 1.0) return vec4(0.0, 0.502, 0.502, 0.0);
	float px = uCell / uGS;
	vec2 st = t / px - 0.5;
	vec2 i = floor(st);
	vec2 f = st - i;
	vec2 f2 = f * f;
	vec2 f3 = f2 * f;
	vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
	vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
	vec2 w3 = f3 / 6.0;
	vec2 w2 = 1.0 - w0 - w1 - w3;
	vec2 a0 = w0 + w1;
	vec2 a1 = w2 + w3;
	vec2 p0 = (i - 0.5 + w1 / a0) * px;
	vec2 p1 = (i + 1.5 + w3 / a1) * px;
	return a0.y * (a0.x * texture2D(uTex, vec2(p0.x, p0.y)) + a1.x * texture2D(uTex, vec2(p1.x, p0.y)))
		+ a1.y * (a0.x * texture2D(uTex, vec2(p0.x, p1.y)) + a1.x * texture2D(uTex, vec2(p1.x, p1.y)));
}

// clear rings round bodies, exact per pixel: a ring is only a few grid cells wide. same math as FogGrid.Shape.keepAt
// except wob, which bends the edge of a plain ring by the fog noise
float keep(vec2 q, float wob) {
	float k = 1.0;
	for (int i = 0; i < 32; i++) {
		if (float(i) >= uRN) break;
		vec4 r = uRing[i];
		vec3 hz = uRingH[i];
		float dist = distance(q, r.xy);
		if (hz.z > 0.0) k *= mix(smoothstep(r.z, r.w, dist), smoothstep(hz.x, hz.y, dist), hz.z);
		else k *= clamp((dist * (1.0 + wob) - r.z) / (r.w - r.z), 0.0, 1.0);
	}
	return k;
}

void main() {
	vec4 s = g(w);
	vec2 D = (s.gb - 0.502) * 1405.0;
	float spd = s.a * 400.0;

	// lit edges come from the grid only, a clear ring draws no outline
	float e = uCell * 0.6;
	float gx = g(w + vec2(e, 0.0)).r - g(w - vec2(e, 0.0)).r;
	float gy = g(w + vec2(0.0, e)).r - g(w - vec2(0.0, e)).r;
	float rim = clamp(length(vec2(gx, gy)) * 3.2, 0.0, 1.0);

	vec2 p = w / 1500.0;
	vec2 wp = vec2(fbm(p * 0.6 + uTime * 0.010), fbm(p * 0.6 + 5.2 - uTime * 0.012));
	vec2 q = p + 1.5 * wp - D / 1500.0;
	vec2 t1 = uTime * vec2(0.006, 0.003);
	float n1 = fbm(q + t1);
	float n1b = fbm(q + t1 + vec2(0.035, 0.035));
	float n2 = fbm(q * 2.2 - uTime * vec2(0.009, -0.004) + 3.1);
	float n3 = fbm(q * 4.5 + uTime * vec2(-0.012, 0.007) + 8.3);
	float d = s.r * 1.3 * keep(w, (n3 - 0.5) * 0.35);
	float lit = clamp((n1 - n1b) * 9.0, -1.0, 1.0);
	float whorl = 1.0 - smoothstep(0.02, 0.16, abs(n2 - 0.5));
	whorl *= smoothstep(0.35, 0.65, fbm(p * 1.3 + 2.7));
	float cover = d * (0.55 + 0.55 * n1 + 0.25 * n3);
	// long soft ramp at the edge
	float c2 = clamp(cover / 0.85, 0.0, 1.0);
	float a = pow(c2 * c2 * (3.0 - 2.0 * c2), 1.1) * 0.94;

	// sunlight: fog near a star takes the star's shades and gets brighter, strongest star wins
	float ws = 0.0;
	vec3 s0 = uC0;
	vec3 s1 = uC1;
	vec3 s2 = uC2;
	for (int i = 0; i < 3; i++) {
		if (float(i) >= uSunN) break;
		vec4 sun = uSun[i];
		float sd = (distance(w, sun.xy) - sun.z) / max(1.0, sun.w - sun.z);
		float l = 1.0 - smoothstep(0.0, 1.0, sd * (0.85 + 0.3 * n1));
		if (l > ws) {
			ws = l;
			s0 = uSunC[i * 3];
			s1 = uSunC[i * 3 + 1];
			s2 = uSunC[i * 3 + 2];
		}
	}
	vec3 k2 = mix(uC2, s2, ws);

	vec3 c = mix(mix(uC0, s0, ws), mix(uC1, s1, ws), smoothstep(0.2, 0.8, n1 * 0.7 + n3 * 0.3));
	c = mix(c, k2, clamp(0.18 + lit * 0.35 + rim * 0.5 + spd / 700.0, 0.0, 1.0) * 0.55);
	c *= 1.0 - whorl * 0.28 * smoothstep(0.2, 0.6, d);
	c *= 0.82 + 0.25 * smoothstep(0.2, 1.0, d);
	c = mix(c, s2, ws * 0.6);
	a = min(1.0, a * (1.0 + ws * 0.6));

	for (int i = 0; i < 48; i++) {
		if (float(i) >= uHN) break;
		vec3 hl = uHoles[i];
		a *= smoothstep(hl.z * 0.45, hl.z * 1.5, distance(w, hl.xy));
	}

	a *= uAlpha;
	gl_FragColor = vec4(c * a, a);
}
