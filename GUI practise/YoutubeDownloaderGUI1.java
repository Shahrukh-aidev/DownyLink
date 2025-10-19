import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Paths;
import java.util.regex.*;

public class YoutubeDownloaderGUI extends JFrame {
    private JTextField urlField = new JTextField(25), outputPathField = new JTextField("downloads", 25);
    private JTextArea logArea = new JTextArea();
    private JButton downloadBtn = createBtn("Start Download", new Color(0, 191, 165), 140, 40),
                    pauseBtn = createBtn("Pause", new Color(255, 87, 34), 100, 40);
    private JProgressBar progressBar = new JProgressBar() {{
        setUI(new BasicProgressBarUI() {
            protected void paintDeterminate(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D)g;
                int w = getWidth(), h = getHeight(), p = getAmountFull(getInsets(), w, h);
                g2.setColor(new Color(50,50,50));
                g2.fillRoundRect(0,0,w,h,15,15);
                g2.setPaint(new GradientPaint(0,0,new Color(0,191,165),w,0,new Color(0,150,135)));
                g2.fillRoundRect(2,2,p-4,h-4,10,10);
                g2.setColor(Color.WHITE);
                g2.drawString(getValue()+"%", (w-g2.getFontMetrics().stringWidth(getValue()+"%"))/2, 
                            (h - g2.getFontMetrics().getHeight())/2 + g2.getFontMetrics().getAscent());
            }
        });
        setStringPainted(true);
        setForeground(Color.WHITE);
        setFont(new Font("Segoe UI", Font.BOLD, 14));
    }};
    private JComboBox<String> formatCombo = new JComboBox<>(new String[]{"Best Quality","MP4","WEBM","720p","1080p","Audio Only"});
    private Process currentProcess;
    private volatile boolean isDownloading = false, isPaused = false;

    public YoutubeDownloaderGUI() {
        super("Professional YouTube Downloader");
        setSize(1000,700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10,10)) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D)g;
                g2.setPaint(new GradientPaint(0,0,new Color(28,28,28),getWidth(),getHeight(),new Color(40,40,40)));
                g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        addComponent(inputPanel, new JLabel("Video URL:"), gbc,0,0);
        urlField.setBackground(new Color(60,60,60));
        urlField.setForeground(Color.WHITE);
        inputPanel.add(urlField, pos(gbc,1,0,3,1));
        
        addComponent(inputPanel, new JLabel("Format:"), gbc,0,1);
        formatCombo.setBackground(new Color(60,60,60));
        formatCombo.setForeground(Color.WHITE);
        inputPanel.add(formatCombo, pos(gbc,1,1,1,1));
        
        addComponent(inputPanel, new JLabel("Save To:"), gbc,0,2);
        outputPathField.setBackground(new Color(60,60,60));
        outputPathField.setForeground(Color.WHITE);
        inputPanel.add(outputPathField, pos(gbc,1,2,1,1));
        JButton browseBtn = createBtn("Browse", new Color(100,100,100), 80,30);
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if(fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                outputPathField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        inputPanel.add(browseBtn, pos(gbc,2,2,1,1));
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        pauseBtn.setEnabled(false);
        pauseBtn.addActionListener(e -> {
            isPaused = !isPaused;
            if(isPaused) { currentProcess.destroy(); pauseBtn.setText("Resume"); }
            else startDownload();
        });
        downloadBtn.addActionListener(e -> {
            if(isDownloading) stopDownload();
            else startDownload();
        });
        btnPanel.add(pauseBtn);
        btnPanel.add(downloadBtn);
        inputPanel.add(btnPanel, pos(gbc,0,3,4,1));
        
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN,12));
        logArea.setForeground(Color.WHITE);
        logArea.setBackground(new Color(40,40,40,200));
        
        mainPanel.add(inputPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        add(mainPanel);
    }

    private void startDownload() {
        if(urlField.getText().isEmpty() || outputPathField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        isDownloading = true;
        downloadBtn.setText("Stop Download");
        pauseBtn.setEnabled(true);
        new SwingWorker<Void, String>() {
            protected Void doInBackground() throws Exception {
                ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--newline", "--download-archive", 
                    Paths.get(outputPathField.getText(), "archive.txt").toString(), "--continue",
                    "--external-downloader", "aria2c", "--external-downloader-args", "-c -j 3 -s 3 -x 3",
                    "-o", outputPathField.getText()+"/%(title)s.%(ext)s", "--format", 
                    formatCombo.getSelectedIndex()==0?"best":formatCombo.getSelectedIndex()==1?"mp4":
                    formatCombo.getSelectedIndex()==2?"webm":formatCombo.getSelectedIndex()==3?
                    "bestvideo[height<=720]+bestaudio":formatCombo.getSelectedIndex()==4?
                    "bestvideo[height<=1080]+bestaudio":"bestaudio", urlField.getText());
                try {
                    currentProcess = pb.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()));
                    String line;
                    while((line = reader.readLine()) != null) {
                        publish(line);
                        Matcher m = Pattern.compile("(\\d+\\.\\d)%").matcher(line);
                        if(m.find()) progressBar.setValue((int)Float.parseFloat(m.group(1)));
                    }
                } catch (IOException ex) { publish("Error: "+ex.getMessage()); }
                return null;
            }
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(line -> logArea.append(line+"\n"));
            }
            protected void done() {
                isDownloading = false;
                downloadBtn.setText("Start Download");
                pauseBtn.setEnabled(false);
            }
        }.execute();
    }

    private void stopDownload() {
        if(currentProcess != null) currentProcess.destroy();
        isDownloading = false;
    }

    private JButton createBtn(String text, Color bg, int w, int h) {
        JButton btn = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setColor(getModel().isRollover()?bg.darker():bg);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),15,15);
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setPreferredSize(new Dimension(w,h));
        return btn;
    }

    private void addComponent(JPanel p, Component c, GridBagConstraints gbc, int x, int y) {
        gbc.gridx = x;
        gbc.gridy = y;
        p.add(c, gbc);
    }

    private GridBagConstraints pos(GridBagConstraints gbc, int x, int y, int w, int h) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = w;
        gbc.gridheight = h;
        return gbc;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new YoutubeDownloaderGUI().setVisible(true));
    }
}