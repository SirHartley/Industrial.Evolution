uniform vec2 uAnchor;

varying vec2 w;

void main() {
	gl_Position = ftransform();
	w = gl_Vertex.xy - uAnchor;
}
