/**
 * 路由器 - 负责根据请求路径分发到对应的处理器
 */

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
    public HttpResponse route(HttpRequestParser.HttpRequest request) {
        if (request == null) {
            return new HttpResponse()
                    .status(400)
                    .contentType("text/plain; charset=utf-8")
                    .body("Bad Request");
        }

        String path = request.getPath();
        String method = request.getMethod();

        System.out.println("[Router] " + method + " " + path);

        // 路由匹配
        switch (path) {
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
                    // StaticFileHandler 负责安全校验（路径穿越检查）、ETag/If-None-Match、Last-Modified 等
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
                    // 其它异常或 StaticFileHandler 未命中 -> 404 Not Found
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
                "<html><head><meta charset='utf-8'><title>用户系统</title></head>" +
                "<body>" +
                "<h1>欢迎来到用户系统</h1>" +
                "<h2>注册</h2>" +
                "<form action='/register' method='POST'>" +
                "  用户名: <input name='username' type='text'><br>" +
                "  密码: <input name='password' type='password'><br>" +
                "  <button type='submit'>注册</button>" +
                "</form>" +
                "<h2>登录</h2>" +
                "<form action='/login' method='POST'>" +
                "  用户名: <input name='username' type='text'><br>" +
                "  密码: <input name='password' type='password'><br>" +
                "  <button type='submit'>登录</button>" +
                "</form>" +
                "</body></html>";
    }
}