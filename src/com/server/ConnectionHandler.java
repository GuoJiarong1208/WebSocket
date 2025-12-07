package com.server;

import com.http.HttpRequestParser;
import com.http.HttpResponse;
import com.http.Router;

import java.io.*;
import java.net.Socket;
import java.io.IOException;
//import java.net.http.Server.HttpResponse;

public class ConnectionHandler implements Runnable{
    private Socket clientSocket;    //记录客户端socket
    private static final Router router = new Router();

    public ConnectionHandler(Socket clientSocket){
        this.clientSocket=clientSocket;
    }

    @Override
    public void run(){      //threadPool.execute()调用
        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            boolean isAlive = true;
            clientSocket.setSoTimeout(100); // 10 秒无数据自动断开

            while (isAlive) {
                // 读取请求头
                // 1. 读取请求行和请求头
                StringBuilder requestBuilder = new StringBuilder();
                String line;
                int contentLength = 0;
                boolean keepAliveRequested = false;//客户端是否请求保持连接

                while ((line = in.readLine()) != null && line.length() != 0) {
                    requestBuilder.append(line).append("\r\n");

                    if (line.toLowerCase().startsWith("content-length:")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            contentLength = Integer.parseInt(parts[1].trim());
                        }
                    }
                    //从请求头中提取Connection,检查是否为长连接
                    if (line.toLowerCase().startsWith("connection:")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            String connValue = parts[1].trim().toLowerCase();
                            keepAliveRequested = connValue.equals("keep-alive");
                        }
                    }

                }

                if (requestBuilder.length() == 0) {
                    break; // 没有数据 -> 客户端关闭
                }

                requestBuilder.append("\r\n");

                // 2. 读取请求体（如果有Content-Length）
                String body = "";
                if (contentLength > 0) {
                    char[] bodyChars = new char[contentLength];
                    int totalRead = 0;
                    while (totalRead < contentLength) {
                        int read = in.read(bodyChars, totalRead, contentLength - totalRead);
                        if(read == -1) break;
                        totalRead += read;
                    }
                    body = new String(bodyChars, 0, totalRead);
                }

                // 3. 组合完整请求
                String fullRequest = requestBuilder.toString() + body;

                System.out.println("===== Received Request =====");
                System.out.println(fullRequest);
                HttpRequestParser.HttpRequest req = null;
                try {
                    HttpRequestParser parser = new HttpRequestParser();
                    req = parser.parse(new ByteArrayInputStream(requestBuilder.toString().getBytes()), body);

                    HttpResponse response = router.route(req);

                    out.write(response.toBytes());
                    out.flush();

                    isAlive = req.isKeepAlive() && keepAliveRequested;
                    System.out.println("[Server.ConnectionHandler] Keep-Alive: " + isAlive);

                    if (!isAlive) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("[?] Request handling error: " + e.getMessage());
                    // 返回 500 响应并关闭连接（也可按需求根据 keep-alive 决定）
                    HttpResponse err = new HttpResponse()
                            .status(500)
                            .contentType("text/plain; charset=utf-8")
                            .body("Internal Server Error");
                    try {
                        out.write(err.toBytes());
                        out.flush();
                    } catch (IOException ex) {
                        // 忽略写入错误
                    }
                    // 出错后关闭连接
                    break;
                }
            }

            clientSocket.close();
            System.out.println("[-] Connection closed: " + clientSocket.getInetAddress());
        }catch (java.net.SocketTimeoutException e) {
            System.err.println("[Timeout] No data received for 10s, closing connection");
        }catch (IOException e){
            System.err.println("[?] Connection error: " + e.getMessage());
        }
    }
}
