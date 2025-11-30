import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 更美观的 HTTP GUI 客户端（带 Login/Register）
 * 增强：在 Auth 面板中友好显示登录/注册结果 —— 从服务器返回的 HTML 中提取“欢迎，用户名！”并以彩色提示（绿色成功 / 红色失败）
 *
 * 放到 src/ 下，在 IDEA 中运行此类的 main 方法。
 */
public class HttpGuiAuthClient2 extends JFrame {
    // Shared controls
    private final JTextField baseUrlField = new JTextField("http://localhost:8080/");
    private final JCheckBox followRedirectsCb = new JCheckBox("自动跟随重定向", true);
    private final JCheckBox keepAliveCb = new JCheckBox("Connection: keep-alive", false);
    private final JCheckBox useLastEtagCb = new JCheckBox("Use Last ETag", false);

    // Auth controls
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JButton registerBtn = new JButton("注册");
    private final JButton loginBtn = new JButton("登录");
    private final JTextArea authResultArea = new JTextArea();
    // friendly status label for auth results
    private final JLabel authStatusLabel = new JLabel(" ");

    // Raw request controls
    private final JComboBox<String> methodCombo = new JComboBox<>(new String[]{"GET", "POST"});
    private final JTextField urlPathField = new JTextField("/");
    private final JTextArea requestBodyArea = new JTextArea(6, 40);
    private final JButton sendBtn = new JButton("发送请求");

    // Response display
    private final JTextArea headersArea = new JTextArea();
    private final JEditorPane bodyPane = new JEditorPane();
    private final JButton saveBodyBtn = new JButton("保存响应到文件");
    private final JLabel statusLabel = new JLabel("就绪");

    // Last response bytes for saving & last ETag
    private byte[] lastResponseBytes = null;
    private String lastContentType = "application/octet-stream";
    private volatile String lastEtag = null;

    public HttpGuiAuthClient2() {
        super("HTTP 客户端（带登录/注册）");
        initLookAndFeel();
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private void initUI() {
        // Top bar: base URL and options
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel urlPanel = new JPanel(new BorderLayout(6, 6));
        urlPanel.add(new JLabel("服务器地址: "), BorderLayout.WEST);
        urlPanel.add(baseUrlField, BorderLayout.CENTER);
        topBar.add(urlPanel, BorderLayout.CENTER);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        opts.add(followRedirectsCb);
        opts.add(keepAliveCb);
        opts.add(useLastEtagCb);
        topBar.add(opts, BorderLayout.EAST);

        // Left: operations (Auth + Raw)
        JTabbedPane leftTabs = new JTabbedPane();
        leftTabs.setBorder(new EmptyBorder(8,8,8,8));
        leftTabs.addTab("登录 / 注册", buildAuthPanel());
        leftTabs.addTab("原始请求", buildRawRequestPanel());

        // Right: response (headers + body)
        JPanel rightPanel = new JPanel(new BorderLayout(8,8));
        rightPanel.setBorder(new EmptyBorder(8,8,8,8));

        headersArea.setEditable(false);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headersArea.setBackground(new Color(245, 247, 250));
        headersArea.setBorder(BorderFactory.createTitledBorder("响应头 / 状态"));

        bodyPane.setEditable(false);
        bodyPane.setContentType("text/html");
        JScrollPane bodyScroll = new JScrollPane(bodyPane);
        bodyScroll.setBorder(BorderFactory.createTitledBorder("响应内容"));

        JPanel rightTop = new JPanel(new BorderLayout(6,6));
        rightTop.add(new JScrollPane(headersArea), BorderLayout.CENTER);

        JPanel rightBottom = new JPanel(new BorderLayout(6,6));
        rightBottom.add(bodyScroll, BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveBodyBtn.setEnabled(false);
        rightButtons.add(saveBodyBtn);
        rightBottom.add(rightButtons, BorderLayout.SOUTH);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTop, rightBottom);
        rightSplit.setResizeWeight(0.3);
        rightPanel.add(rightSplit, BorderLayout.CENTER);

        // Main split: left operations / right response
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabs, rightPanel);
        mainSplit.setResizeWeight(0.33);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4,8,4,8));
        statusBar.add(statusLabel, BorderLayout.WEST);

        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        c.add(topBar, BorderLayout.NORTH);
        c.add(mainSplit, BorderLayout.CENTER);
        c.add(statusBar, BorderLayout.SOUTH);

        // Actions
        registerBtn.addActionListener(e -> onAuthAction("/register"));
        loginBtn.addActionListener(e -> onAuthAction("/login"));
        sendBtn.addActionListener(e -> onSendRaw());
        saveBodyBtn.addActionListener(e -> onSaveBody());
    }

    private JPanel buildAuthPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("用户名:"), g);
        g.gridx = 1; g.anchor = GridBagConstraints.WEST;
        form.add(usernameField, g);

        g.gridx = 0; g.gridy = 1; g.anchor = GridBagConstraints.EAST;
        form.add(new JLabel("密码:"), g);
        g.gridx = 1; g.anchor = GridBagConstraints.WEST;
        form.add(passwordField, g);

        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btns.add(registerBtn);
        btns.add(loginBtn);
        form.add(btns, g);

        // status label (friendly) above result area
        authStatusLabel.setOpaque(true);
        authStatusLabel.setBackground(new Color(250, 250, 250));
        authStatusLabel.setBorder(new EmptyBorder(6,6,6,6));
        authStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        authStatusLabel.setPreferredSize(new Dimension(200, 30));

        authResultArea.setEditable(false);
        authResultArea.setLineWrap(true);
        authResultArea.setWrapStyleWord(true);
        JScrollPane resultScroll = new JScrollPane(authResultArea);
        resultScroll.setPreferredSize(new Dimension(200, 200));
        resultScroll.setBorder(BorderFactory.createTitledBorder("操作结果"));

        p.add(form, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout(6,6));
        bottom.add(authStatusLabel, BorderLayout.NORTH);
        bottom.add(resultScroll, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRawRequestPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        top.add(new JLabel("方法:"));
        top.add(methodCombo);
        top.add(new JLabel("路径:"));
        urlPathField.setPreferredSize(new Dimension(280, 24));
        top.add(urlPathField);
        top.add(sendBtn);

        requestBodyArea.setLineWrap(true);
        requestBodyArea.setBorder(BorderFactory.createTitledBorder("请求体 (POST)"));

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(requestBodyArea), BorderLayout.CENTER);
        return p;
    }

    private void setStatus(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(s));
    }

    private void appendHeaders(String s) {
        SwingUtilities.invokeLater(() -> headersArea.setText(s));
    }

    private void setBodyText(String text, boolean isHtml) {
        SwingUtilities.invokeLater(() -> {
            if (isHtml) {
                bodyPane.setContentType("text/html");
                bodyPane.setText(text);
            } else {
                bodyPane.setContentType("text/plain");
                bodyPane.setText("<pre style='white-space:pre-wrap; font-family:monospace;'>" + escapeHtml(text) + "</pre>");
            }
            bodyPane.setCaretPosition(0);
        });
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // 保存响应到文件
    private void onSaveBody() {
        if (lastResponseBytes == null) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(lastResponseBytes);
                JOptionPane.showMessageDialog(this, "保存成功：" + f.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // 发送原始请求
    private void onSendRaw() {
        String method = (String) methodCombo.getSelectedItem();
        String base = baseUrlField.getText().trim();
        String path = urlPathField.getText().trim();
        if (base.isEmpty()) { JOptionPane.showMessageDialog(this, "请先填写服务器地址"); return; }
        if (!path.startsWith("/")) path = "/" + path;
        String full = joinUrl(base, path);
        String body = requestBodyArea.getText();
        sendBtn.setEnabled(false);
        setStatus("发送中...");
        new RequestWorker(method, full, body, followRedirectsCb.isSelected(), keepAliveCb.isSelected(), true).execute();
    }

    // 处理登录/注册（重用请求 worker，但结果去 authResultArea）
    private void onAuthAction(String endpoint) {
        String base = baseUrlField.getText().trim();
        if (base.isEmpty()) { JOptionPane.showMessageDialog(this, "请先填写服务器地址"); return; }
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) { JOptionPane.showMessageDialog(this, "用户名或密码不能为空"); return; }
        String url = joinUrl(base, endpoint);
        String body = "username=" + urlEncode(user) + "&password=" + urlEncode(pass);
        registerBtn.setEnabled(false);
        loginBtn.setEnabled(false);
        // reset auth status label
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setText(" ");
            authStatusLabel.setForeground(Color.BLACK);
            authStatusLabel.setBackground(new Color(250,250,250));
        });
        setStatus((endpoint.equals("/register") ? "注册中..." : "登录中..."));
        authResultArea.setText("");
        new RequestWorker("POST", url, body, followRedirectsCb.isSelected(), keepAliveCb.isSelected(), false).execute();
    }

    private String joinUrl(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) return base.substring(0, base.length()-1) + path;
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }

    // Worker 负责网络 IO
    private class RequestWorker extends SwingWorker<Void, Void> {
        private final String method;
        private String urlStr;
        private final String body;
        private final boolean followRedirects;
        private final boolean keepAlive;
        private final boolean displayInRaw;

        RequestWorker(String method, String urlStr, String body, boolean followRedirects, boolean keepAlive, boolean displayInRaw) {
            this.method = method;
            this.urlStr = urlStr;
            this.body = body == null ? "" : body;
            this.followRedirects = followRedirects;
            this.keepAlive = keepAlive;
            this.displayInRaw = displayInRaw;
        }

        @Override
        protected Void doInBackground() {
            try {
                int redirects = 0;
                String curMethod = method;
                while (true) {
                    if (redirects > 5) { setStatus("重定向次数过多"); break; }
                    URL url = new URL(urlStr);
                    int port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();
                    String host = url.getHost();
                    // debug print
                    System.out.println("[Client] Connecting to " + host + ":" + port + " url=" + urlStr);
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 4000);
                        System.out.println("[Client] Connected");
                        socket.setKeepAlive(keepAlive);
                        socket.setSoTimeout(10000);
                        OutputStream out = socket.getOutputStream();
                        InputStream in = socket.getInputStream();
                        BufferedOutputStream bout = new BufferedOutputStream(out);

                        String path = url.getPath();
                        if (path == null || path.isEmpty()) path = "/";
                        if (url.getQuery() != null && !url.getQuery().isEmpty()) path += "?" + url.getQuery();

                        StringBuilder req = new StringBuilder();
                        req.append(curMethod).append(" ").append(path).append(" HTTP/1.1\r\n");
                        req.append("Host: ").append(host).append("\r\n");
                        req.append("User-Agent: SimpleGuiClient/1.0\r\n");
                        req.append("Accept: */*\r\n");
                        req.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");

                        // If user checked "Use Last ETag" and lastEtag exists, add If-None-Match header
                        if (useLastEtagCb.isSelected() && lastEtag != null) {
                            req.append("If-None-Match: ").append(lastEtag).append("\r\n");
                        }

                        byte[] bodyBytes = new byte[0];
                        if ("POST".equalsIgnoreCase(curMethod)) {
                            bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                            req.append("Content-Type: application/x-www-form-urlencoded; charset=utf-8\r\n");
                            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                        }
                        req.append("\r\n");
                        bout.write(req.toString().getBytes(StandardCharsets.UTF_8));
                        if (bodyBytes.length > 0) bout.write(bodyBytes);
                        bout.flush();

                        System.out.println("[Client] Request sent, waiting for response...");
                        BufferedInputStream bin = new BufferedInputStream(in);
                        String statusLine = readLine(bin);
                        if (statusLine == null) { publishError("没有收到响应"); return null; }
                        System.out.println("[Client] Status line: " + statusLine);

                        StringBuilder headerBuf = new StringBuilder();
                        headerBuf.append(statusLine).append("\n");
                        Map<String, String> headers = new LinkedHashMap<>();
                        String line;
                        while ((line = readLine(bin)) != null && !line.isEmpty()) {
                            headerBuf.append(line).append("\n");
                            int idx = line.indexOf(':');
                            if (idx > 0) {
                                headers.put(line.substring(0, idx).trim().toLowerCase(), line.substring(idx+1).trim());
                            }
                        }
                        final String headerStr = headerBuf.toString();
                        if (displayInRaw) appendHeaders(headerStr);
                        else SwingUtilities.invokeLater(() -> authResultArea.append(headerStr + "\n"));

                        // capture ETag if present for later use
                        if (headers.containsKey("etag")) {
                            lastEtag = headers.get("etag");
                            SwingUtilities.invokeLater(() -> statusLabel.setText("Captured ETag: " + lastEtag));
                        }

                        int statusCode = parseStatus(statusLine);

                        if ((statusCode == 301 || statusCode == 302) && followRedirects) {
                            String loc = headers.get("location");
                            if (loc == null) { publishError("重定向但无 Location"); break; }
                            urlStr = resolveLocation(url, loc);
                            curMethod = "GET"; // common behavior after POST redirect
                            redirects++;
                            continue;
                        }

                        if (statusCode == 304) {
                            publishInfo("304 Not Modified (无正文)");
                            SwingUtilities.invokeLater(() -> {
                                if (displayInRaw) setBodyText("", false);
                                else authResultArea.append("304 Not Modified\n");
                            });
                            break;
                        }

                        // 读取 body（支持 Content-Length 或直到流结束）
                        byte[] respBody;
                        String cl = headers.get("content-length");
                        if (cl != null) {
                            int len = Integer.parseInt(cl);
                            respBody = readFixedBytes(bin, len);
                        } else {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[4096];
                            int r;
                            while ((r = bin.read(buf)) != -1) {
                                baos.write(buf, 0, r);
                            }
                            respBody = baos.toByteArray();
                        }
                        lastResponseBytes = respBody;
                        lastContentType = headers.getOrDefault("content-type", "application/octet-stream");
                        boolean isHtml = lastContentType.toLowerCase().contains("html");

                        if (displayInRaw) {
                            if (isTextContent(lastContentType)) {
                                String text = new String(respBody, StandardCharsets.UTF_8);
                                setBodyText(text, isHtml);
                            } else {
                                setBodyText("[二进制内容：" + respBody.length + " bytes]\nContent-Type: " + lastContentType, false);
                                SwingUtilities.invokeLater(() -> saveBodyBtn.setEnabled(true));
                            }
                        } else {
                            if (isTextContent(lastContentType)) {
                                String text = new String(respBody, StandardCharsets.UTF_8);
                                // append raw HTML/text to authResultArea
                                SwingUtilities.invokeLater(() -> authResultArea.append(text + "\n"));
                                // extract welcome message and set friendly label
                                String welcome = extractWelcomeFromHtml(text);
                                if (statusCode == 200 && welcome != null) {
                                    SwingUtilities.invokeLater(() -> {
                                        authStatusLabel.setText("登录成功 — 欢迎，" + welcome + " !");
                                        authStatusLabel.setForeground(new Color(0, 120, 20)); // green
                                        authStatusLabel.setBackground(new Color(230, 255, 230));
                                    });
                                } else if (statusCode >= 400) {
                                    // show failure reason (use first line of text or server message)
                                    String reason = extractFirstTextLine(text);
                                    SwingUtilities.invokeLater(() -> {
                                        authStatusLabel.setText("操作失败: " + reason);
                                        authStatusLabel.setForeground(Color.RED);
                                        authStatusLabel.setBackground(new Color(255, 230, 230));
                                    });
                                } else {
                                    // neutral info (e.g., redirected to login page)
                                    SwingUtilities.invokeLater(() -> {
                                        authStatusLabel.setText("服务器响应: " + statusCode);
                                        authStatusLabel.setForeground(Color.DARK_GRAY);
                                        authStatusLabel.setBackground(new Color(250, 250, 250));
                                    });
                                }
                            } else {
                                SwingUtilities.invokeLater(() -> authResultArea.append("[二进制响应，长度 " + respBody.length + " bytes]\n"));
                                SwingUtilities.invokeLater(() -> saveBodyBtn.setEnabled(true));
                                SwingUtilities.invokeLater(() -> {
                                    authStatusLabel.setText("二进制响应 (" + respBody.length + " bytes)");
                                    authStatusLabel.setForeground(Color.DARK_GRAY);
                                    authStatusLabel.setBackground(new Color(250, 250, 250));
                                });
                            }
                        }

                        setStatus("接收完成: " + statusCode);
                        break;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                publishError("网络错误: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    sendBtn.setEnabled(true);
                    registerBtn.setEnabled(true);
                    loginBtn.setEnabled(true);
                });
            }
            return null;
        }

        private String resolveLocation(URL base, String loc) {
            try { return new URL(base, loc).toString(); } catch (MalformedURLException e) { return loc; }
        }

        private int parseStatus(String statusLine) {
            try {
                String[] p = statusLine.split("\\s+");
                return Integer.parseInt(p[1]);
            } catch (Exception e) { return -1; }
        }

        private void publishError(String s) { SwingUtilities.invokeLater(() -> { setStatus(s); JOptionPane.showMessageDialog(HttpGuiAuthClient2.this, s, "错误", JOptionPane.ERROR_MESSAGE); }); }
        private void publishInfo(String s) { SwingUtilities.invokeLater(() -> setStatus(s)); }
    }

    // extract welcome name from HTML text (strip tags and search for "欢迎")
    private String extractWelcomeFromHtml(String html) {
        if (html == null) return null;
        // strip tags
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        // look for 欢迎 ... !
        Pattern p = Pattern.compile("欢迎\\s*[，,]?\\s*([^!\\n\\r]+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            // trim trailing punctuation
            name = name.replaceAll("[！!。\\.,]*$", "").trim();
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    private String extractFirstTextLine(String text) {
        if (text == null) return "";
        // strip tags, then take first non-empty line
        String clean = text.replaceAll("(?s)<[^>]*>", " ");
        for (String line : clean.split("[\\r\\n]+")) {
            String t = line.trim();
            if (!t.isEmpty()) return t.length() > 120 ? t.substring(0, 120) + "..." : t;
        }
        return clean.length() > 120 ? clean.substring(0, 120) + "..." : clean;
    }

    // 工具方法：按 CRLF 读一行（返回不含 CRLF 的字符串），使用 ISO-8859-1 保持 header 原样
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1, cur;
        while ((cur = in.read()) != -1) {
            baos.write(cur);
            if (prev == '\r' && cur == '\n') {
                byte[] arr = baos.toByteArray();
                if (arr.length >= 2) {
                    return new String(arr, 0, arr.length - 2, StandardCharsets.ISO_8859_1);
                }
                return "";
            }
            prev = cur;
        }
        if (baos.size() == 0) return null;
        return new String(baos.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private byte[] readFixedBytes(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r == -1) break;
            off += r;
        }
        if (off == len) return buf;
        byte[] r2 = new byte[off];
        System.arraycopy(buf, 0, r2, 0, off);
        return r2;
    }

    private boolean isTextContent(String ct) {
        if (ct == null) return false;
        String l = ct.toLowerCase();
        return l.startsWith("text/") || l.contains("json") || l.contains("xml") || l.contains("html") || l.contains("javascript");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HttpGuiAuthClient2::new);
    }
}