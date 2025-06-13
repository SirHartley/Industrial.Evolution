

uniform sampler2D tex;
uniform sampler2D noiseTex1;

vec2 texCoord = gl_TexCoord[0].xy;

uniform float level;
uniform vec3 colorMult;

void main() {

	vec4 col = texture2D(tex, texCoord);

	float brightness = (col.r + col.g + col.b);

	//col.g = texture2D(tex, texCoord + offset).g;
	//col.r = texture2D(tex, texCoord).r;
	//col.b = texture2D(tex, texCoord - offset).b;

	col.r *= 1.0 + (colorMult.r * level * 0.9);
	col.g *= 1.0 + (colorMult.g * level * 0.9);
	col.b *= 1.0 + (colorMult.b * level * 0.9);

	col.r += (colorMult.r * level * brightness * 0.9);
	col.g += (colorMult.g * level * brightness * 0.9);
	col.b += (colorMult.b * level * brightness * 0.9);


	gl_FragColor = col;

}