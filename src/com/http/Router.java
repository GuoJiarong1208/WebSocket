package com.http; /**
 * 路由器 - 负责根据请求路径分发到对应的处理器
 */

import com.controller.UserController;
import com.server.StaticFileHandler;

import java.io.File;
import java.io.IOException;

public class Router {
    private final UserController userController;
    private final StaticFileHandler staticFileHandler;

    public Router() {
        this.userController = new UserController();
        this.staticFileHandler = new StaticFileHandler(new File("www"));
    }

    /**
     * 路由分发入口
     * @param request HTTP请求对象
     * @return HTTP响应对象
     */
    public HttpResponse route(HttpRequestParser.HttpRequest request) throws IOException {
        if (request == null) {
            return new HttpResponse()
                    .status(400)
                    .contentType("text/plain; charset=utf-8")
                    .body("Bad Request");
        }

        String path = request.getPath();
        String method = request.getMethod();

        System.out.println("[Server.Router] " + method + " " + path);

        // 路由匹配
        switch (path) {
            case "/bug":
                throw new IOException("故意制造异常");

            case "/register":
                return userController.handleRegister(request);

            case "/login":
                return userController.handleLogin(request);

            case "/":
                // 主页
                return new HttpResponse()
                        .status(200)
                        .contentType("text/html; charset=utf-8")
                        .body(getHomePage())
                        .keepAlive(request.isKeepAlive());

            case "/moved": // <-- 新增测试路由，返回 301 永久重定向到 /
                return new HttpResponse()
                        .status(301)
                        .header("Location", "/")
                        .body("<html><body>Moved permanently to <a href=\"/\">/</a></body></html>")
                        .keepAlive(request.isKeepAlive());

            default:
                // 尝试作为静态文件处理（去掉查询参数）
                String pathNoQuery = path.split("\\?", 2)[0];
                try {
                    // Server.StaticFileHandler 负责安全校验（路径穿越检查）、ETag/If-None-Match、Last-Modified 等
                    HttpResponse staticResp = staticFileHandler.handle(new HttpRequestParser.HttpRequest(
                            request.getMethod(), pathNoQuery, request.getVersion(), request.getHeaders(), request.getBody()));
                    // 保持与请求相同的 keep-alive 策略
                    staticResp.keepAlive(request.isKeepAlive());
                    return staticResp;
                } catch (IOException e) {
                    // 文件读取或 IO 问题 -> 500
                    e.printStackTrace();
                    return new HttpResponse()
                            .status(500)
                            .contentType("text/plain; charset=utf-8")
                            .body("Internal Server Error")
                            .keepAlive(request.isKeepAlive());
                } catch (Exception e) {
                    // 其它异常或 Server.StaticFileHandler 未命中 -> 404 Not Found
                    return new HttpResponse()
                            .status(404)
                            .contentType("text/html; charset=utf-8")
                            .body("<html><body><h1>404 Not Found</h1><p>路径 " + path + " 不存在</p></body></html>")
                            .keepAlive(request.isKeepAlive());
                }
        }
    }

    /**
     * 主页HTML（提供注册和登录的表单）
     */
    private String getHomePage() {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "  <meta charset='utf-8'>" +
                "  <title>用户系统</title>" +
                "  <style>" +
                "    body { font-family: -apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;" +
                "           background: #f5f7fa; margin: 0; padding: 0; }" +
                "    .container { max-width: 800px; margin: 40px auto; padding: 0 16px; }" +
                "    .title { text-align: center; margin-bottom: 24px; color: #34495e; }" +
                "    .subtitle { color: #7f8c8d; text-align: center; margin-bottom: 32px; }" +
                "    .card-wrapper { display: flex; flex-wrap: wrap; gap: 16px; justify-content: center; }" +
                "    .card { background: #ffffff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);" +
                "            padding: 24px 24px 20px; flex: 1 1 260px; max-width: 360px; box-sizing: border-box; }" +
                "    .card h2 { margin-top: 0; margin-bottom: 16px; font-size: 20px; color: #2c3e50; }" +
                "    .form-row { margin-bottom: 12px; }" +
                "    .form-row label { display: inline-block; width: 70px; color: #555; }" +
                "    .form-row input { width: 70%; max-width: 220px; padding: 6px 8px; border-radius: 4px;" +
                "                      border: 1px solid #dcdfe6; box-sizing: border-box; }" +
                "    .form-row input:focus { outline: none; border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,0.15); }" +
                "    .btn-primary { display: inline-block; margin-top: 8px; padding: 6px 18px; border-radius: 4px;" +
                "                  border: none; cursor: pointer; background: #409eff; color: #fff; font-size: 14px; }" +
                "    .btn-primary:hover { background: #66b1ff; }" +
                "    .btn-primary:active { background: #3a8ee6; }" +
                "    .tip { font-size: 12px; color: #95a5a6; margin-top: 6px; }" +
                "    .footer { text-align: center; margin-top: 32px; font-size: 12px; color: #b0b3b8; }" +
                "    @media (max-width: 640px) { .card-wrapper { flex-direction: column; } .card { max-width: 100%; } }" +
                "  </style>" +
                "</head>" +
                "<body>" +
                "  <div class='container'>" +
                "    <h1 class='title'>欢迎来到用户系统</h1>" +
                "    <div class='subtitle'>请先注册账号，或使用已有账号登录。</div>" +
                "    <div class='card-wrapper'>" +
                "      <div class='card'>" +
                "        <h2>注册</h2>" +
                "        <form action='/register' method='POST'>" +
                "          <div class='form-row'>" +
                "            <label>用户名</label>" +
                "            <input name='username' type='text' required>" +
                "          </div>" +
                "          <div class='form-row'>" +
                "            <label>密码</label>" +
                "            <input name='password' type='password' required>" +
                "          </div>" +
                "          <button type='submit' class='btn-primary'>注册</button>" +
                "          <div class='tip'>密码请不要过于简单，避免使用生日等容易被猜到的信息。</div>" +
                "        </form>" +
                "      </div>" +
                "      <div class='card'>" +
                "        <h2>登录</h2>" +
                "        <form action='/login' method='POST'>" +
                "          <div class='form-row'>" +
                "            <label>用户名</label>" +
                "            <input name='username' type='text' required>" +
                "          </div>" +
                "          <div class='form-row'>" +
                "            <label>密码</label>" +
                "            <input name='password' type='password' required>" +
                "          </div>" +
                "          <button type='submit' class='btn-primary'>登录</button>" +
                "          <div class='tip'>登录失败时，请确认账号密码是否正确。</div>" +
                "        </form>" +
                "      </div>" +
                "    </div>" +
                "    <div class='footer'>简易示例系统，仅用于本地学习和测试。</div>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }
}