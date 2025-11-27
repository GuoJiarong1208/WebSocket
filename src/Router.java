/**
 * 路由器 - 负责根据请求路径分发到对应的处理器
 */
public class Router {
    private final UserController userController;

    public Router() {
        this.userController = new UserController();
    }

    /**
     * 路由分发入口
     * @param request HTTP请求对象
     * @return HTTP响应对象
     */
    public HttpResponse route(HttpRequestParser.HttpRequest request) {
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

            default:
                // 404 Not Found
                return new HttpResponse()
                        .status(404)
                        .contentType("text/html; charset=utf-8")
                        .body("<html><body><h1>404 Not Found</h1><p>路径 " + path + " 不存在</p></body></html>")
                        .keepAlive(request.isKeepAlive());
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