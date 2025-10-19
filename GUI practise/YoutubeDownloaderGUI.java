import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeDownloaderGUI extends JFrame {
    private JTextField urlField;
    private JTextArea logArea;
    private JButton downloadButton;
    private JButton pauseButton;
    private JProgressBar progressBar;
    private JComboBox<String> formatCombo;
    private JTextField outputPathField;
    private volatile boolean isDownloading = false;
    private Process currentProcess;
    private JLabel statusLabel;
    private Timer progressTimer;
    private boolean isPaused = false;

    public YoutubeDownloaderGUI() {
        super("Professional YouTube Downloader");
        initializeUI();
    }

    private void initializeUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        
        // Main panel with gradient background
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                Color color1 = new Color(28, 28, 28);
                Color color2 = new Color(40, 40, 40);
                GradientPaint gp = new GradientPaint(0, 0, color1, getWidth(), getHeight(), color2);
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Input panel
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.NORTH);

        // Status label
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(new Color(0, 191, 165));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        logArea.setBackground(new Color(40, 40, 40, 200));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setCaretColor(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(60, 60, 60)),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setUI(new GradientProgressBarUI());
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        progressBar.setForeground(Color.WHITE);
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        
        // Glass pane for progress
        JPanel glassPane = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                g2d.setColor(new Color(40, 40, 40));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.dispose();
            }
        };
        glassPane.setLayout(new GridBagLayout());
        glassPane.setVisible(false);
        setGlassPane(glassPane);

        JPanel progressContainer = new JPanel(new BorderLayout());
        progressContainer.setOpaque(false);
        progressContainer.add(progressBar, BorderLayout.CENTER);
        progressContainer.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));
        glassPane.add(progressContainer);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        add(mainPanel);
        createMenuBar();
        setupProgressAnimation();
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URL Input
        addLabeledComponent(panel, "Video URL:", 0, 0, gbc);
        urlField = createStyledTextField();
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        panel.add(urlField, position(gbc, 1, 0));

        // Format Selection
        addLabeledComponent(panel, "Format:", 0, 1, gbc);
        String[] formats = {"Best Quality", "MP4", "WEBM", "720p", "1080p", "Audio Only"};
        formatCombo = new JComboBox<>(formats);
        styleComboBox(formatCombo);
        panel.add(formatCombo, position(gbc, 1, 1));

        // Output Path
        addLabeledComponent(panel, "Save To:", 0, 2, gbc);
        outputPathField = createStyledTextField();
        outputPathField.setText("downloads");
        panel.add(outputPathField, position(gbc, 1, 2));
        
        JButton browseBtn = createHoverButton("Browse", new Color(100, 100, 100), new Color(150, 150, 150));
        browseBtn.addActionListener(e -> browseFolder());
        panel.add(browseBtn, position(gbc, 2, 2));

        // Control Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);

        downloadButton = createHoverButton("Start Download", new Color(0, 191, 165), new Color(0, 150, 135));
        downloadButton.setPreferredSize(new Dimension(140, 40));
        downloadButton.addActionListener(this::toggleDownload);

        pauseButton = createHoverButton("Pause", new Color(255, 87, 34), new Color(200, 60, 20));
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> togglePause());

        btnPanel.add(pauseButton);
        btnPanel.add(downloadButton);

        gbc.gridwidth = 4;
        panel.add(btnPanel, position(gbc, 0, 3));

        return panel;
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setForeground(Color.WHITE);
        
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.setBackground(new Color(40, 40, 40));
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void setupProgressAnimation() {
        progressTimer = new Timer(50, e -> {
            if (isDownloading && !isPaused) {
                progressBar.repaint();
            }
        });
    }

    private void toggleDownload(ActionEvent e) {
        if (isDownloading) {
            stopDownload();
        } else {
            startDownload();
        }
    }

    private void startDownload() {
        String url = urlField.getText().trim();
        String outputPath = outputPathField.getText().trim();

        if (url.isEmpty() || outputPath.isEmpty()) {
            showError("Input Error", "Please fill in all required fields");
            return;
        }

        isDownloading = true;
        getGlassPane().setVisible(true);
        statusLabel.setText("Downloading...");
        progressTimer.start();
        downloadButton.setText("Stop Download");
        pauseButton.setEnabled(true);
        logArea.setText("");
        progressBar.setValue(0);

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    Path outputDir = Paths.get(outputPath);
                    Path archivePath = outputDir.resolve("archive.txt");

                    ProcessBuilder pb = new ProcessBuilder(
                        "yt-dlp",
                        "--newline",
                        "--download-archive", archivePath.toString(),
                        "--continue",
                        "--external-downloader", "aria2c",
                        "--external-downloader-args", "-c -j 3 -s 3 -x 3",
                        "-o", outputPath + "/%(title)s.%(ext)s",
                        "--format", getSelectedFormat(),
                        "--no-warnings",
                        url
                    );

                    currentProcess = pb.start();
                    readStream(currentProcess.getInputStream(), false);
                    readStream(currentProcess.getErrorStream(), true);

                    int exitCode = currentProcess.waitFor();
                    publish("\nProcess exited with code: " + exitCode);
                } catch (IOException | InterruptedException ex) {
                    publish("Error: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(line -> {
                    logArea.append(line + "\n");
                    parseProgress(line);
                });
            }

            @Override
            protected void done() {
                isDownloading = false;
                getGlassPane().setVisible(false);
                progressTimer.stop();
                downloadButton.setText("Start Download");
                pauseButton.setEnabled(false);
                statusLabel.setText("Download Complete");
            }
        }.execute();
    }

    private String getSelectedFormat() {
        String selected = (String) formatCombo.getSelectedItem();
        return switch (selected) {
            case "MP4" -> "mp4";
            case "WEBM" -> "webm";
            case "720p" -> "bestvideo[height<=720]+bestaudio";
            case "1080p" -> "bestvideo[height<=1080]+bestaudio";
            case "Audio Only" -> "bestaudio";
            default -> "best";
        };
    }

    private void readStream(InputStream stream, boolean isError) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    SwingUtilities.invokeLater(() -> {
                        logArea.append((isError ? "ERROR: " : "") + finalLine + "\n");
                        parseProgress(finalLine);
                    });
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> 
                    logArea.append("Stream error: " + ex.getMessage() + "\n"));
            }
        }).start();
    }

    private void parseProgress(String line) {
        Matcher matcher = Pattern.compile("(\\d+\\.\\d)%").matcher(line);
        if (matcher.find()) {
            int progress = (int) Float.parseFloat(matcher.group(1));
            progressBar.setValue(progress);
            statusLabel.setText(String.format("Downloading... %d%% Complete", progress));
        }
    }

    private void stopDownload() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
        }
        isDownloading = false;
        getGlassPane().setVisible(false);
        progressTimer.stop();
        statusLabel.setText("Download Stopped");
        logArea.append("\nDownload stopped by user\n");
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy();
            }
            statusLabel.setText("Download Paused");
            pauseButton.setText("Resume");
            logArea.append("Download paused\n");
        } else {
            startDownload();
            pauseButton.setText("Pause");
        }
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
            "Professional YouTube Downloader\nVersion 2.1\nMIT License 2024",
            "About",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private JTextField createStyledTextField() {
        JTextField field = new JTextField(25);
        field.setBackground(new Color(60, 60, 60));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }

    private JButton createHoverButton(String text, Color baseColor, Color hoverColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (getModel().isRollover()) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(baseColor);
                }
                
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.setColor(Color.WHITE);
                super.paintComponent(g2);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(140, 40);
            }
        };
        
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return button;
    }

    private void styleComboBox(JComboBox<String> combo) {
        combo.setBackground(new Color(60, 60, 60));
        combo.setForeground(Color.WHITE);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? new Color(0, 150, 135) : new Color(60, 60, 60));
                setForeground(Color.WHITE);
                return this;
            }
        });
    }

    private GridBagConstraints position(GridBagConstraints gbc, int x, int y) {
        gbc.gridx = x;
        gbc.gridy = y;
        return gbc;
    }

    private void addLabeledComponent(JPanel panel, String text, int x, int y, GridBagConstraints gbc) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(200, 200, 200));
        panel.add(label, position(gbc, x, y));
    }

    class GradientProgressBarUI extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = progressBar.getWidth();
            int height = progressBar.getHeight();
            int progress = getAmountFull(progressBar.getInsets(), width, height);

            // Background
            g2d.setColor(new Color(50, 50, 50));
            g2d.fillRoundRect(0, 0, width, height, 15, 15);

            // Progress gradient
            GradientPaint gp = new GradientPaint(
                0, 0, new Color(0, 191, 165),
                width, 0, new Color(0, 150, 135)
            );
            g2d.setPaint(gp);
            g2d.fillRoundRect(2, 2, progress - 4, height - 4, 10, 10);

            // Glow effect
            if (isDownloading && !isPaused) {
                int glowWidth = (int) (20 * Math.sin(System.currentTimeMillis() % 1000 / 1000.0 * Math.PI * 2));
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(progress - glowWidth, 2, glowWidth, height - 4, 10, 10);
            }

            // Text
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();
            String text = progressBar.getValue() + "%";
            int x = (width - fm.stringWidth(text)) / 2;
            int y = (height - fm.getHeight()) / 2 + fm.getAscent();
            g2d.drawString(text, x, y);

            g2d.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {}
            
            YoutubeDownloaderGUI gui = new YoutubeDownloaderGUI();
            gui.setVisible(true);
            gui.setMinimumSize(new Dimension(800, 500));
        });
    }
}