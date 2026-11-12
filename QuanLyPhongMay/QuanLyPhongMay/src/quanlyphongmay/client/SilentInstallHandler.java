package quanlyphongmay.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SilentInstallHandler - Tiếp nhận file cài đặt từ Server, lưu vào thư mục tạm
 * và tự động thực thi cài đặt ngầm (Silent Install) không cần thao tác người dùng.
 */
public class SilentInstallHandler {

    private final String maMay;
    private final Consumer<String> resultSender;

    private String currentFileName = null;
    private long expectedFileSize = 0;
    private String silentArgs = "";
    private List<byte[]> chunks = null;

    public SilentInstallHandler(String maMay, Consumer<String> resultSender) {
        this.maMay = maMay;
        this.resultSender = resultSender;
    }

    public synchronized void handleStart(String fileName, long fileSize, String args) {
        this.currentFileName = fileName;
        this.expectedFileSize = fileSize;
        this.silentArgs = (args == null) ? "" : args.trim();
        this.chunks = new ArrayList<>();
    }

    public synchronized void handleChunk(String base64Chunk) {
        if (chunks != null && base64Chunk != null) {
            try {
                byte[] data = Base64.getDecoder().decode(base64Chunk);
                chunks.add(data);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public synchronized void handleEnd(String finalFileName) {
        if (currentFileName == null || chunks == null) {
            return;
        }

        final String fileName = (finalFileName != null && !finalFileName.isEmpty()) ? finalFileName : currentFileName;
        final List<byte[]> fileChunks = new ArrayList<>(chunks);
        final String args = silentArgs;

        // Reset state
        currentFileName = null;
        expectedFileSize = 0;
        silentArgs = "";
        chunks = null;

        // Tiến hành ghi file và kích hoạt cài đặt ngầm trong luồng riêng
        Thread installThread = new Thread(() -> {
            try {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "LANPC_Install");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                File installerFile = new File(tempDir, fileName);

                // Ghi dữ liệu file
                try (FileOutputStream fos = new FileOutputStream(installerFile)) {
                    for (byte[] c : fileChunks) {
                        fos.write(c);
                    }
                }

                // Thực thi cài đặt ngầm
                executeSilentInstall(installerFile, args);

            } catch (Exception e) {
                sendResult(fileName, false, -1, "Lỗi ghi file cài đặt: " + e.getMessage());
            }
        }, "SilentInstaller-" + fileName);

        installThread.setDaemon(true);
        installThread.start();
    }

    private void executeSilentInstall(File installerFile, String args) {
        String lowerName = installerFile.getName().toLowerCase();
        List<String> cmd = new ArrayList<>();

        if (lowerName.endsWith(".msi")) {
            cmd.add("msiexec.exe");
            cmd.add("/i");
            cmd.add(installerFile.getAbsolutePath());
            if (args.isEmpty()) {
                cmd.add("/qn");
                cmd.add("/norestart");
            } else {
                for (String arg : args.split("\\s+")) {
                    if (!arg.isEmpty()) cmd.add(arg);
                }
            }
        } else if (lowerName.endsWith(".bat") || lowerName.endsWith(".cmd")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(installerFile.getAbsolutePath());
        } else if (lowerName.endsWith(".ps1")) {
            cmd.add("powershell.exe");
            cmd.add("-ExecutionPolicy");
            cmd.add("Bypass");
            cmd.add("-File");
            cmd.add(installerFile.getAbsolutePath());
        } else {
            // Cho file .exe
            cmd.add(installerFile.getAbsolutePath());
            if (!args.isEmpty()) {
                for (String arg : args.split("\\s+")) {
                    if (!arg.isEmpty()) cmd.add(arg);
                }
            } else {
                // Mặc định flag silent thông dụng nếu không truyền
                cmd.add("/VERYSILENT");
                cmd.add("/SUPPRESSMSGBOXES");
                cmd.add("/NORESTART");
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Chờ tiến trình cài đặt hoàn thành trong tối đa 10 phút
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (finished) {
                int exitCode = process.exitValue();
                if (exitCode == 0 || exitCode == 3010) { // 3010 = reboot required nhưng cài đặt thành công
                    sendResult(installerFile.getName(), true, exitCode, "Cài đặt ngầm thành công (Exit Code " + exitCode + ")");
                } else {
                    sendResult(installerFile.getName(), false, exitCode, "Tiến trình cài đặt trả về mã lỗi: " + exitCode);
                }
            } else {
                process.destroyForcibly();
                sendResult(installerFile.getName(), false, -1, "Quá thời gian chờ cài đặt (Timeout 10 phút)");
            }
        } catch (Exception e) {
            sendResult(installerFile.getName(), false, -1, "Lỗi thực thi cài đặt: " + e.getMessage());
        }
    }

    private void sendResult(String fileName, boolean success, int exitCode, String message) {
        // Protocol: INSTALL_RESULT|maMay|fileName|SUCCESS/FAILED|exitCode|message
        String status = success ? "SUCCESS" : "FAILED";
        String packet = "INSTALL_RESULT|" + maMay + "|" + fileName + "|" + status + "|" + exitCode + "|" + message.replace("|", "/");
        resultSender.accept(packet);
    }
}

