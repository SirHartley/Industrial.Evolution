package indevo.exploration.fog.sim;

public class FogCover {

    public static final int BLUR_PASSES = 3;
    private static final int MARGIN = BLUR_PASSES + 1;

    private final float[] cover;
    private final int nx, ny;
    private final float x0, y0, cell;

    // tiles[i][j] >= 0 is a present tile, i along x and j along y, the field centred on centreX, centreY
    public FogCover(int[][] tiles, float tileSize, float centreX, float centreY) {
        int tx = tiles.length, ty = tx == 0 ? 0 : tiles[0].length;
        nx = tx * 2 + MARGIN * 2;
        ny = ty * 2 + MARGIN * 2;
        cell = tileSize / 2f;
        x0 = centreX - tx * tileSize / 2f - MARGIN * cell;
        y0 = centreY - ty * tileSize / 2f - MARGIN * cell;

        float[] a = new float[nx * ny], b = new float[nx * ny];
        for (int i = 0; i < tx; i++) for (int j = 0; j < ty; j++) {
            if (tiles[i][j] < 0) continue;
            int ci = MARGIN + i * 2, cj = MARGIN + j * 2;
            a[cj * nx + ci] = a[cj * nx + ci + 1] = a[(cj + 1) * nx + ci] = a[(cj + 1) * nx + ci + 1] = 1f;
        }
        // box blur over half tiles: about 0.5 on the edge of a tile block, 0 a tile and a half out
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                float sum = 0f;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int ii = i + di, jj = j + dj;
                    if (ii >= 0 && jj >= 0 && ii < nx && jj < ny) sum += a[jj * nx + ii];
                }
                b[j * nx + i] = sum / 9f;
            }
            float[] swap = a;
            a = b;
            b = swap;
        }
        cover = a;
    }

    public float at(float x, float y) {
        float gx = (x - x0) / cell - 0.5f, gy = (y - y0) / cell - 0.5f;
        if (gx < 0f || gy < 0f || gx >= nx - 1 || gy >= ny - 1) return 0f;
        int i = (int) gx, j = (int) gy, k = j * nx + i;
        float fx = gx - i, fy = gy - j;
        return (cover[k] * (1f - fx) + cover[k + 1] * fx) * (1f - fy) + (cover[k + nx] * (1f - fx) + cover[k + nx + 1] * fx) * fy;
    }
}
