package Client;// java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 更美观的 HTTP GUI 客户端（带 Login/Register）
 * 增强：在 Auth 面板中友好显示登录/注册结果 —— 从服务器返回的 HTML 中提取“欢迎，用户名！”并以彩色提示（绿色成功 / 红色失败）
 *
 * 放到 src/ 下，在 IDEA 中运行此类的 main 方法。
 */
public class HttpClient extends JFrame {
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

    public HttpClient() {
        super("HTTP 客户端（带登录/注册）");
        initLookAndFeel();

        // 全局字体与基础背景（仅影响外观）
        Font uiFont = new Font("Microsoft YaHei", Font.PLAIN, 13);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                UIManager.put(key, uiFont);
            }
        }
        getContentPane().setBackground(new Color(245, 247, 250));

        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 720);
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
        topBar.setBackground(new Color(250, 252, 255));

        JPanel urlPanel = new JPanel(new BorderLayout(6, 6));
        urlPanel.setBackground(topBar.getBackground());
        urlPanel.add(new JLabel("服务器地址: "), BorderLayout.WEST);
        urlPanel.add(baseUrlField, BorderLayout.CENTER);
        topBar.add(urlPanel, BorderLayout.CENTER);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        opts.setBackground(topBar.getBackground());
        opts.add(followRedirectsCb);
        opts.add(keepAliveCb);
        opts.add(useLastEtagCb);
        topBar.add(opts, BorderLayout.EAST);

        // Left: operations (Auth + Raw)
        JTabbedPane leftTabs = new JTabbedPane();
        leftTabs.setBorder(new EmptyBorder(8,8,8,8));
        leftTabs.setBackground(new Color(245, 247, 250));

        leftTabs.addTab("登录 / 注册", buildAuthPanel());
        leftTabs.addTab("原始请求", buildRawRequestPanel());

        // Right: response (headers + body)
        JPanel rightPanel = new JPanel(new BorderLayout(8,8));
        rightPanel.setBorder(new EmptyBorder(8,8,8,8));
        rightPanel.setBackground(new Color(245, 247, 250));

        // 响应头卡片
        JPanel headersCard = new JPanel(new BorderLayout(4, 4));
        headersCard.setBackground(Color.WHITE);
        headersCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 233)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel headersTitle = new JLabel("响应头 / 状态");
        headersTitle.setFont(headersTitle.getFont().deriveFont(Font.BOLD, 12f));
        headersTitle.setForeground(new Color(55, 65, 81));
        headersArea.setEditable(false);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headersArea.setBackground(new Color(250, 250, 252));
        JScrollPane headersScroll = new JScrollPane(headersArea);
        headersScroll.setBorder(null);
        headersCard.add(headersTitle, BorderLayout.NORTH);
        headersCard.add(headersScroll, BorderLayout.CENTER);

        // 响应体卡片
        JPanel bodyCard = new JPanel(new BorderLayout(4, 4));
        bodyCard.setBackground(Color.WHITE);
        bodyCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 233)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel bodyTitle = new JLabel("响应内容");
        bodyTitle.setFont(bodyTitle.getFont().deriveFont(Font.BOLD, 12f));
        bodyTitle.setForeground(new Color(55, 65, 81));

        bodyPane.setEditable(false);
        bodyPane.setContentType("text/html");
        bodyPane.setBackground(new Color(252, 252, 252));
        JScrollPane bodyScroll = new JScrollPane(bodyPane);
        bodyScroll.getViewport().setBackground(new Color(252, 252, 252));
        bodyScroll.setBorder(null);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.setOpaque(false);
        saveBodyBtn.setEnabled(false);
        styleSecondaryButton(saveBodyBtn);
        rightButtons.add(saveBodyBtn);

        bodyCard.add(bodyTitle, BorderLayout.NORTH);
        bodyCard.add(bodyScroll, BorderLayout.CENTER);
        bodyCard.add(rightButtons, BorderLayout.SOUTH);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, headersCard, bodyCard);
        rightSplit.setResizeWeight(0.3);
        rightSplit.setBorder(null);

        rightPanel.add(rightSplit, BorderLayout.CENTER);

        // Server.Main split: left operations / right response
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTabs, rightPanel);
        mainSplit.setResizeWeight(0.33);

        // Status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4,8,4,8));
        statusBar.setBackground(new Color(240, 242, 245));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
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
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBackground(new Color(245, 247, 250));

        // 顶部提示
        JPanel tipPanel = new JPanel(new BorderLayout());
        tipPanel.setBackground(new Color(228, 239, 255));
        tipPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(191, 219, 254)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel tipLabel = new JLabel("在这里通过表单调用 `/register` 和 `/login` 接口，结果会在下方高亮显示。");
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.PLAIN, 12f));
        tipLabel.setForeground(new Color(30, 64, 175));
        tipPanel.add(tipLabel, BorderLayout.WEST);

        // 表单卡片
        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 233)),
                new EmptyBorder(16, 18, 14, 18)
        ));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.gridx = 0; g.gridy = 0; g.anchor = GridBagConstraints.WEST;
        JLabel title = new JLabel("账号登录 / 注册");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(new Color(31, 41, 55));
        card.add(title, g);

        g.gridy++;
        JLabel subTitle = new JLabel("先注册再登录，或者直接用已有账号登录。");
        subTitle.setFont(subTitle.getFont().deriveFont(Font.PLAIN, 12f));
        subTitle.setForeground(new Color(107, 114, 128));
        card.add(subTitle, g);

        g.gridy++;
        g.gridx = 0; g.anchor = GridBagConstraints.EAST;
        card.add(new JLabel("用户名:"), g);
        g.gridx = 1; g.anchor = GridBagConstraints.WEST;
        usernameField.setPreferredSize(new Dimension(180, 26));
        card.add(usernameField, g);

        g.gridy++;
        g.gridx = 0; g.anchor = GridBagConstraints.EAST;
        card.add(new JLabel("密码:"), g);
        g.gridx = 1; g.anchor = GridBagConstraints.WEST;
        passwordField.setPreferredSize(new Dimension(180, 26));
        card.add(passwordField, g);

        g.gridy++;
        g.gridx = 0; g.gridwidth = 2; g.anchor = GridBagConstraints.CENTER;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btns.setOpaque(false);
        stylePrimaryButton(registerBtn);
        stylePrimaryButton(loginBtn);
        btns.add(registerBtn);
        btns.add(loginBtn);
        card.add(btns, g);

        // 状态 & 结果卡片
        JPanel bottomCard = new JPanel(new BorderLayout(6, 6));
        bottomCard.setBackground(Color.WHITE);
        bottomCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 233)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // status label (friendly) above result area
        authStatusLabel.setOpaque(true);
        authStatusLabel.setBackground(new Color(248, 250, 252));
        authStatusLabel.setForeground(new Color(75, 85, 99));
        authStatusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        authStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        authStatusLabel.setPreferredSize(new Dimension(200, 34));
        authStatusLabel.setFont(authStatusLabel.getFont().deriveFont(Font.PLAIN, 12.5f));

        authResultArea.setEditable(false);
        authResultArea.setLineWrap(true);
        authResultArea.setWrapStyleWord(true);
        authResultArea.setBackground(new Color(252, 252, 252));
        authResultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        authResultArea.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane resultScroll = new JScrollPane(authResultArea);
        resultScroll.setPreferredSize(new Dimension(200, 200));
        resultScroll.getViewport().setBackground(new Color(252, 252, 252));
        resultScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                "原始响应内容"
        ));

        bottomCard.add(authStatusLabel, BorderLayout.NORTH);
        bottomCard.add(resultScroll, BorderLayout.CENTER);

        outer.add(tipPanel, BorderLayout.NORTH);
        outer.add(card, BorderLayout.CENTER);
        outer.add(bottomCard, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildRawRequestPanel() {
        JPanel outer = new JPanel(new BorderLayout(8, 8));
        outer.setBackground(new Color(245, 247, 250));

        JPanel tipPanel = new JPanel(new BorderLayout());
        tipPanel.setBackground(new Color(254, 249, 235));
        tipPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(253, 230, 138)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        JLabel tipLabel = new JLabel("原始请求模式：你可以手工输入路径、方法和请求体，直接发送 HTTP 请求。");
        tipLabel.setFont(tipLabel.getFont().deriveFont(Font.PLAIN, 12f));
        tipLabel.setForeground(new Color(146, 64, 14));
        tipPanel.add(tipLabel, BorderLayout.WEST);

        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 226, 233)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.setOpaque(false);
        top.add(new JLabel("方法:"));
        top.add(methodCombo);
        top.add(new JLabel("路径:"));
        urlPathField.setPreferredSize(new Dimension(320, 26));
        top.add(urlPathField);
        stylePrimaryButton(sendBtn);
        top.add(sendBtn);

        requestBodyArea.setLineWrap(true);
        requestBodyArea.setWrapStyleWord(true);
        requestBodyArea.setBackground(new Color(252, 252, 252));
        requestBodyArea.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                "请求体 (POST 表单原文)"
        ));

        card.add(top, BorderLayout.NORTH);
        card.add(new JScrollPane(requestBodyArea), BorderLayout.CENTER);

        outer.add(tipPanel, BorderLayout.NORTH);
        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    // ---- UI 样式辅助 ----

    private void stylePrimaryButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(new Color(37, 99, 235));
        btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleSecondaryButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(new Color(243, 244, 246));
        btn.setForeground(new Color(55, 65, 81));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void styleCheckBox(JCheckBox cb) {
        cb.setOpaque(false);
        cb.setForeground(new Color(209, 213, 219));
        cb.setFont(cb.getFont().deriveFont(Font.PLAIN, 12f));
    }

    // ---- 逻辑相关：保持你原来的实现 ----

    private void setStatus(String s) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(s));
    }

    private void appendHeaders(String s) {
        SwingUtilities.invokeLater(() -> headersArea.setText(s));
    }

    private void setBodyText(String text, boolean isHtml) {
        SwingUtilities.invokeLater(() -> {
            if (isHtml) {
                bodyPane.setContentType("text/html;charset=utf-8");
                bodyPane.setText(text);
            } else {
                bodyPane.setContentType("text/plain;charset=utf-8");
                bodyPane.setText(text);
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
                setStatus("已保存到: " + f.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage());
            }
        }
    }

    // 发送原始请求
    private void onSendRaw() {
        String method = (String) methodCombo.getSelectedItem();
        String base = baseUrlField.getText().trim();
        String path = urlPathField.getText().trim();
        if (base.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先填写服务器地址");
            return;
        }
        if (!path.startsWith("/")) path = "/" + path;
        String full = joinUrl(base, path);
        String body = requestBodyArea.getText();
        sendBtn.setEnabled(false);
        setStatus("发送中...");
        new RequestWorker(method, full, body, followRedirectsCb.isSelected(),
                keepAliveCb.isSelected(), true, false).execute();
    }

    // 处理登录 / 注册
    private void onAuthAction(String endpoint) {
        String base = baseUrlField.getText().trim();
        if (base.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先填写服务器地址");
            return;
        }
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "用户名或密码不能为空");
            return;
        }
        String url = joinUrl(base, endpoint);
        String body = "username=" + urlEncode(user) + "&password=" + urlEncode(pass);
        registerBtn.setEnabled(false);
        loginBtn.setEnabled(false);
        // reset auth status label
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setText("请求已发送，等待服务器响应...");
            authStatusLabel.setForeground(new Color(75, 85, 99));
            authStatusLabel.setBackground(new Color(248, 250, 252));
        });
        setStatus(endpoint.equals("/register") ? "注册中..." : "登录中...");
        authResultArea.setText("");
        boolean isRegister = "/register".equals(endpoint);
        new RequestWorker(
                "POST",
                url,
                body,
                followRedirectsCb.isSelected(),
                keepAliveCb.isSelected(),
                false,
                isRegister
        ).execute();
    }

    private String joinUrl(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/"))
            return base.substring(0, base.length() - 1) + path;
        if (!base.endsWith("/") && !path.startsWith("/"))
            return base + "/" + path;
        return base + path;
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    // Worker 负责网络 IO
    private class RequestWorker extends SwingWorker<Void, Void> {
        private final String method;
        private String urlStr;
        private final String body;
        private final boolean followRedirects;
        private final boolean keepAlive;
        private final boolean displayInRaw;
        private final boolean isRegister;

        RequestWorker(String method, String urlStr, String body,
                      boolean followRedirects, boolean keepAlive,
                      boolean displayInRaw, boolean isRegister) {
            this.method = method;
            this.urlStr = urlStr;
            this.body = body == null ? "" : body;
            this.followRedirects = followRedirects;
            this.keepAlive = keepAlive;
            this.displayInRaw = displayInRaw;
            this.isRegister = isRegister;
        }

        @Override
        protected Void doInBackground() {
            try {
                int redirects = 0;
                String curMethod = method;
                while (true) {
                    if (redirects > 5) {
                        setStatus("重定向次数过多");
                        break;
                    }
                    URL url = new URL(urlStr);
                    int port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();
                    String host = url.getHost();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 4000);
                        socket.setKeepAlive(keepAlive);
                        socket.setSoTimeout(10000);
                        OutputStream out = socket.getOutputStream();
                        InputStream in = socket.getInputStream();
                        BufferedOutputStream bout = new BufferedOutputStream(out);

                        String path = url.getPath();
                        if (path == null || path.isEmpty()) path = "/";
                        if (url.getQuery() != null && !url.getQuery().isEmpty())
                            path += "?" + url.getQuery();

                        StringBuilder req = new StringBuilder();
                        req.append(curMethod).append(" ").append(path).append(" HTTP/1.1\r\n");
                        req.append("Host: ").append(host).append("\r\n");
                        req.append("User-Agent: SimpleGuiClient/1.0\r\n");
                        req.append("Accept: */*\r\n");
                        req.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");

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

                        BufferedInputStream bin = new BufferedInputStream(in);
                        String statusLine = readLine(bin);
                        if (statusLine == null) {
                            publishError("没有收到响应");
                            return null;
                        }

                        StringBuilder headerBuf = new StringBuilder();
                        headerBuf.append(statusLine).append("\n");
                        Map<String, String> headers = new LinkedHashMap<>();
                        String line;
                        while ((line = readLine(bin)) != null && !line.isEmpty()) {
                            headerBuf.append(line).append("\n");
                            int idx = line.indexOf(':');
                            if (idx > 0) {
                                String name = line.substring(0, idx).trim().toLowerCase();
                                String val = line.substring(idx + 1).trim();
                                headers.put(name, val);
                            }
                        }
                        final String headerStr = headerBuf.toString();
                        if (displayInRaw) appendHeaders(headerStr);
                        else SwingUtilities.invokeLater(() -> authResultArea.append(headerStr + "\n"));

                        if (headers.containsKey("etag")) {
                            lastEtag = headers.get("etag");
                            SwingUtilities.invokeLater(() -> statusLabel.setText("Captured ETag: " + lastEtag));
                        }

                        int statusCode = parseStatus(statusLine);

                        if ((statusCode == 301 || statusCode == 302) && followRedirects) {
                            String loc = headers.get("location");
                            if (loc == null) {
                                publishError("重定向但无 Location");
                                break;
                            }
                            urlStr = resolveLocation(url, loc);
                            curMethod = "GET";
                            redirects++;
                            continue;
                        }

                        if (statusCode == 304) {
                            publishInfo("304 Not Modified (无正文)");
                            SwingUtilities.invokeLater(() -> {
                                if (displayInRaw) {
                                    setBodyText("[304 Not Modified 无正文]", false);
                                } else {
                                    authResultArea.append("[304 Not Modified 无正文]\n");
                                }
                            });
                            break;
                        }

                        byte[] respBody;
                        String cl = headers.get("content-length");
                        if (cl != null) {
                            int len = Integer.parseInt(cl.trim());
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
                                SwingUtilities.invokeLater(() -> authResultArea.append(text + "\n"));
                                String welcome = extractWelcomeFromHtml(text);

                                if (statusCode == 200) {
                                    SwingUtilities.invokeLater(() -> {
                                        if (welcome != null) {
                                            authStatusLabel.setText(welcome);
                                        } else {
                                            String firstLine = extractFirstTextLine(text);
                                            authStatusLabel.setText(firstLine.isEmpty() ? "操作成功" : firstLine);
                                        }
                                        authStatusLabel.setForeground(new Color(22, 101, 52));
                                        authStatusLabel.setBackground(new Color(220, 252, 231));
                                    });
                                } else {
                                    SwingUtilities.invokeLater(() -> {
                                        String firstLine = extractFirstTextLine(text);
                                        authStatusLabel.setText(firstLine.isEmpty() ? ("操作失败，HTTP " + statusCode) : firstLine);
                                        authStatusLabel.setForeground(new Color(153, 27, 27));
                                        authStatusLabel.setBackground(new Color(254, 226, 226));
                                    });
                                }
                            } else {
                                SwingUtilities.invokeLater(() -> authResultArea.append("[二进制响应，长度 " + respBody.length + " bytes]\n"));
                                SwingUtilities.invokeLater(() -> saveBodyBtn.setEnabled(true));
                                SwingUtilities.invokeLater(() -> {
                                    authStatusLabel.setText("收到二进制响应，长度 " + respBody.length + " bytes");
                                    authStatusLabel.setForeground(new Color(55, 65, 81));
                                    authStatusLabel.setBackground(new Color(248, 250, 252));
                                });
                            }
                        }

                        publishInfo("完成，HTTP " + statusCode);
                        break;
                    }
                }
            } catch (Exception e) {
                publishError("请求失败: " + e.getMessage());
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
            try {
                URL u = new URL(base, loc);
                return u.toString();
            } catch (MalformedURLException e) {
                return loc;
            }
        }

        private int parseStatus(String statusLine) {
            try {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) return Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
            return -1;
        }

        private void publishError(String s) {
            SwingUtilities.invokeLater(() -> {
                setStatus(s);
                JOptionPane.showMessageDialog(HttpClient.this, s, "错误", JOptionPane.ERROR_MESSAGE);
            });
        }

        private void publishInfo(String s) {
            SwingUtilities.invokeLater(() -> setStatus(s));
        }
    }

    // 从 HTML 文本中提取“欢迎 xxx”之类的欢迎语
    private String extractWelcomeFromHtml(String html) {
        if (html == null) return null;
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        Pattern p = Pattern.compile("欢迎\\s*[，,]?\\s*([^!\\n\\r<]+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return "登录成功！ 欢迎，" + m.group(1).trim();
        }
        return null;
    }

    private String extractFirstTextLine(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("(?s)<[^>]*>", " ");
        for (String line : clean.split("[\\r\\n]+")) {
            String s = line.trim();
            if (!s.isEmpty()) {
                return s.length() > 120 ? s.substring(0, 120) + "..." : s;
            }
        }
        return clean.length() > 120 ? clean.substring(0, 120) + "..." : clean;
    }

    // 工具方法：按 CRLF 读一行
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1, cur;
        while ((cur = in.read()) != -1) {
            baos.write(cur);
            if (prev == '\r' && cur == '\n') {
                byte[] arr = baos.toByteArray();
                if (arr.length == 2 && arr[0] == '\r' && arr[1] == '\n') {
                    return "";
                }
                return new String(arr, 0, arr.length - 2, StandardCharsets.ISO_8859_1);
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
        return l.startsWith("text/") || l.contains("json") || l.contains("xml")
                || l.contains("html") || l.contains("javascript");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HttpClient::new);
    }
}
