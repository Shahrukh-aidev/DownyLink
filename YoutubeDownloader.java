import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class YoutubeDownloader {
    private Process process;
    private boolean isPaused = false;
    private String lastUrl;
    private String lastOutputPath;

    public void downloadVideo(String url, String outputPath) {
        lastUrl = url;
        lastOutputPath = outputPath;
        isPaused = false;

        try {
           

            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--newline", // better output formatting
               
                "--continue", // allows resuming
                "-o", outputPath + "/%(title)s.%(ext)s",
                "--no-warnings",
                url
            );

            process = pb.start();

            // Create threads to read output and error streams
            Thread outputThread = new Thread(new StreamGobbler(process.getInputStream(), "OUTPUT"));
            Thread errorThread = new Thread(new StreamGobbler(process.getErrorStream(), "ERROR"));

            outputThread.start();
            errorThread.start();

            int exitCode = process.waitFor();
            outputThread.join();
            errorThread.join();

            if (!isPaused) {
                System.out.println("Download finished with exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void pauseDownload() {
        if (process != null && process.isAlive()) {
            process.destroy();
            isPaused = true;
            System.out.println("Download paused. You can resume it manually.");
        }
    }

    public void resumeDownload() {
        if (isPaused && lastUrl != null && lastOutputPath != null) {
            System.out.println("Resuming download...");
            downloadVideo(lastUrl, lastOutputPath);
        } else {
            System.out.println("No paused download to resume.");
        }
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final String streamType;

        StreamGobbler(InputStream inputStream, String streamType) {
            this.inputStream = inputStream;
            this.streamType = streamType;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(streamType + "> " + line);
                }
            } catch (IOException e) {
                System.err.println("Error in " + streamType + " stream: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        YoutubeDownloader downloader = new YoutubeDownloader();

        // Example: Start download
        downloader.downloadVideo(
            "",
            "downloads"
        );

        // Simulate pause/resume
        // You can control this manually or trigger from UI later
        // downloader.pauseDownload();
        // downloader.resumeDownload();
    }
}
