package quanlyphongmay.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * SecurityUtil - Tiện ích mật mã bảo mật định danh và xác thực gói tin
 * Cung cấp mã hóa SHA-256, HMAC-SHA256, sinh Node ID bất biến và Nonce chống Replay Attack.
 */
public final class SecurityUtil {

    public static final String INTERNAL_SYSTEM_SALT = "LANPC_SEC_V2026_KEY";
    public static final String SHARED_HMAC_SECRET  = "LANPC_HMAC_SECRET_SECURE_2026_KEY";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecurityUtil() {}

    /**
     * Băm chuỗi dữ liệu bằng SHA-256 trả về chuỗi Hex viết thường
     */
    public static String sha256Hex(String data) {
        if (data == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(data.hashCode());
        }
    }

    /**
     * Tính toán chữ ký HMAC-SHA256
     */
    public static String hmacSha256(String data, String secretKey) {
        if (data == null || secretKey == null) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Kiểm tra chữ ký HMAC (constant-time comparison để chống timing attack)
     */
    public static boolean verifyHmac(String data, String secretKey, String expectedHmac) {
        if (data == null || secretKey == null || expectedHmac == null) return false;
        String calculated = hmacSha256(data, secretKey);
        return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), expectedHmac.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sinh Node ID bảo mật độc nhất dạng SEC-NODE-<6_HEX>
     * Dựa trên MachineGuid, MAC Address, ProcessorId và khóa muối nội bộ
     */
    public static String generateNodeId(String machineGuid, String mac, String procId) {
        String raw = (machineGuid != null ? machineGuid.trim() : "MG_NULL")
                + "|" + (mac != null ? mac.trim().toUpperCase() : "MAC_NULL")
                + "|" + (procId != null ? procId.trim() : "PROC_NULL")
                + "|" + INTERNAL_SYSTEM_SALT;

        String hash = sha256Hex(raw).toUpperCase();
        String sub = hash.length() >= 6 ? hash.substring(0, 6) : String.format("%06X", raw.hashCode() & 0xFFFFFF);
        return "SEC-NODE-" + sub;
    }

    /**
     * Sinh chuỗi ngẫu nhiên Nonce chống tấn công Replay Attack
     */
    public static String generateNonce(int length) {
        byte[] bytes = new byte[Math.max(8, length)];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, length);
    }
}

