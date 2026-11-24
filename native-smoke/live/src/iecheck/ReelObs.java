package iecheck;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

// test only: one obs-websocket 5 request (OBS 28 or later) per call, -Die.reel.obs=host:port, -Die.reel.obs.password if set in OBS.
// raw socket, java.net.http may be missing from the game's jre
public final class ReelObs {
    private static final Logger log = Global.getLogger(ReelObs.class);
    public static final String PROPERTY = "ie.reel.obs";

    private ReelObs() {
    }

    public static boolean on() {
        String value = System.getProperty(PROPERTY);
        return value != null && !value.isBlank();
    }

    public static void request(String type) {
        if (!on()) return;
        String[] hostPort = System.getProperty(PROPERTY).trim().split(":");
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 4455;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(3000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] key = new byte[16];
            new Random().nextBytes(key);
            out.write(("GET / HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "
                    + Base64.getEncoder().encodeToString(key) + "\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: obswebsocket.json\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String head = head(in);
            if (!head.startsWith("HTTP/1.1 101")) throw new IllegalStateException("no websocket upgrade: " + head.split("\r\n")[0]);

            JSONObject hello = read(in, 0);
            JSONObject identify = new JSONObject();
            identify.put("rpcVersion", 1);
            identify.put("eventSubscriptions", 0);
            JSONObject helloData = hello.getJSONObject("d");
            if (helloData.has("authentication")) {
                JSONObject auth = helloData.getJSONObject("authentication");
                String secret = b64(sha256(System.getProperty(PROPERTY + ".password", "") + auth.getString("salt")));
                identify.put("authentication", b64(sha256(secret + auth.getString("challenge"))));
            }
            send(out, op(1, identify));
            read(in, 2);

            JSONObject request = new JSONObject();
            request.put("requestType", type);
            request.put("requestId", "reel-" + type);
            send(out, op(6, request));
            JSONObject status = read(in, 7).getJSONObject("d").getJSONObject("requestStatus");
            log.info("LIVE_CHECK note: REEL obs " + type + " " + (status.getBoolean("result") ? "ok" : "refused: " + status.optString("comment", "code " + status.optInt("code"))));
        } catch (Exception e) {
            log.info("LIVE_CHECK note: REEL obs " + type + " failed: " + e);
        }
    }

    private static String op(int op, JSONObject data) throws Exception {
        JSONObject message = new JSONObject();
        message.put("op", op);
        message.put("d", data);
        return message.toString();
    }

    private static String head(InputStream in) throws Exception {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) throw new IllegalStateException("connection closed during the handshake");
            head.append((char) b);
        }
        return head.toString();
    }

    // the next text frame with this op, events and pings are skipped
    private static JSONObject read(InputStream in, int op) throws Exception {
        for (int i = 0; i < 20; i++) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) throw new IllegalStateException("connection closed");
            long len = b1 & 0x7f;
            if (len == 126) len = (in.read() << 8) | in.read();
            else if (len == 127) {
                len = 0;
                for (int k = 0; k < 8; k++) len = (len << 8) | in.read();
            }
            byte[] mask = (b1 & 0x80) != 0 ? bytes(in, 4) : null;
            byte[] payload = bytes(in, (int) len);
            if (mask != null) for (int k = 0; k < payload.length; k++) payload[k] ^= mask[k % 4];
            int opcode = b0 & 0x0f;
            if (opcode == 8) throw new IllegalStateException("closed by OBS: " + new String(payload, StandardCharsets.UTF_8));
            if (opcode != 1) continue;
            JSONObject message = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            if (message.getInt("op") == op) return message;
        }
        throw new IllegalStateException("no op " + op + " reply");
    }

    private static byte[] bytes(InputStream in, int n) throws Exception {
        byte[] out = new byte[n];
        int at = 0;
        while (at < n) {
            int got = in.read(out, at, n - at);
            if (got < 0) throw new IllegalStateException("connection closed");
            at += got;
        }
        return out;
    }

    // client frames are masked (rfc 6455 5.3)
    private static void send(OutputStream out, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        int len = payload.length;
        byte[] header = len < 126 ? new byte[]{(byte) 0x81, (byte) (0x80 | len)}
                : new byte[]{(byte) 0x81, (byte) (0x80 | 126), (byte) (len >> 8), (byte) len};
        out.write(header);
        out.write(mask);
        for (int k = 0; k < len; k++) payload[k] ^= mask[k % 4];
        out.write(payload);
        out.flush();
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
