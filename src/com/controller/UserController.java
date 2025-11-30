package com.controller;

import com.http.HttpRequestParser;
import com.http.HttpResponse;
import com.model.UserService;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器 - 处理注册和登录请求
 */
public class UserController {
    private final UserService userService;

    public UserController() {
        this.userService = new UserService();
    }

    /**
     * 处理注册请求
     * POST /register
     * 请求体格式: username=xxx&password=xxx
     */
    public HttpResponse handleRegister(HttpRequestParser.HttpRequest request) {
        // 只接受POST方法
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return new HttpResponse()
                    .status(405)  // Method Not Allowed
                    .contentType("text/plain;charset=UTF-8")
                    .body("只支持POST请求")
                    .keepAlive(request.isKeepAlive());
        }

        // 解析请求体中的参数
        Map<String, String> params = parseFormData(request.getBody());
        String username = params.get("username");
        String password = params.get("password");

        // 调用业务逻辑
        String result = userService.register(username, password);

        // 根据结果构造响应
        if ("注册成功".equals(result)) {
            // 注册成功，显示成功信息并提供返回首页的链接
            return new HttpResponse()
                    .status(200)
                    .contentType("text/html; charset=utf-8")
                    .body("<html><body><h1>注册成功</h1><p>您已成功注册！</p></body></html>")
                    .keepAlive(request.isKeepAlive());
        }else {
            // 注册失败，返回错误信息
            return new HttpResponse()
                    .status(400)  // Bad Request
                    .contentType("text/html; charset=utf-8")
                    .body("<html><body><h1>注册失败</h1><p>" + result + "</p></body></html>")
                    .keepAlive(request.isKeepAlive());
        }
    }

    /**
     * 处理登录请求
     * POST /login
     * 请求体格式: username=xxx&password=xxx
     */
    public HttpResponse handleLogin(HttpRequestParser.HttpRequest request) {
        String method = request.getMethod();

        // GET: 返回登录页
        if ("GET".equalsIgnoreCase(method)) {
            return new HttpResponse()
                    .status(302)
                    .header("Location", "/")
                    .body("<html><body>Go back to <a href=\"/\">/</a></body></html>")
                    .keepAlive(request.isKeepAlive());
        }

        // 只接受 POST 处理登录
        if (!"POST".equalsIgnoreCase(method)) {
            return new HttpResponse()
                    .status(405)
                    .contentType("text/plain;charset=UTF-8")
                    .body("只支持POST请求")
                    .keepAlive(request.isKeepAlive());
        }

        // 解析请求体中的参数
        Map<String, String> params = parseFormData(request.getBody());
        String username = params.get("username");
        String password = params.get("password");

        // 调用业务逻辑
        String result = userService.login(username, password);

        // 根据结果构造响应
        if ("登录成功".equals(result)) {
            return new HttpResponse()
                    . status(200)
                    .contentType("text/html; charset=utf-8")
                    .body("<html><body><h1>欢迎，" + username + "!</h1><p>登录成功</p></body></html>")
                    .keepAlive(request.isKeepAlive());
        } else {
            return new HttpResponse()
                    . status(401)  // Unauthorized
                    .contentType("text/html; charset=utf-8")
                    .body("<html><body><h1>登录失败</h1><p>" + result + "</p></body></html>")
                    .keepAlive(request.isKeepAlive());
        }
    }

    /**
     * 解析表单数据 (application/x-www-form-urlencoded)
     * 格式: key1=value1&key2=value2
     */
    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return params;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], "UTF-8");
                    String value = URLDecoder.decode(kv[1], "UTF-8");
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    //不会到达
                }
            }
        }
        return params;
    }
}