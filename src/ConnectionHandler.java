import java.io.*;
import java.net.Socket;
import java.io.IOException;

public class ConnectionHandler implements Runnable{
    private Socket clientSocket;    //记录客户端socket

    public ConnectionHandler(Socket clientSocket){
        this.clientSocket=clientSocket;
    }

    @Override
    public void run(){      //threadPool.execut用
        try (BufferedReader in =
                     new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            boolean isAlive = true;
            clientSocket.setSoTimeout(10000); // 10 秒无数据自动断开

            while(isAlive){
                // 读取请求头
                StringBuilder requestBuilder = new StringBuilder();
                String requestLine;
                while ((requestLine = in.readLine()) != null && requestLine.length() != 0) {
                    requestBuilder.append(requestLine).append("\r\n");
                }

                if (requestBuilder.length() == 0) {
                    break; // 没有数据 -> 客户端关闭
                }

                System.out.println("===== Received Request =====");
                System.out.println(requestBuilder);

                // 判断是否为长连接
                String requestLower = requestBuilder.toString().toLowerCase();
                isAlive = requestLower.contains("connection: keep-alive");

                // 构造响应内容 写死
                String body = "Hello from BIO HTTP Server!";
                String response =
                        "HTTP/1.1 test01 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: " + body.length() + "\r\n" +
                                (isAlive ? "Connection: keep-alive\r\n" : "Connection: close\r\n") +
                                "\r\n" +
                                body;

                out.write(response.getBytes());
                out.flush();

                if (!isAlive) {
                    break;
                }
            }

            clientSocket.close();
            System.out.println("[-] Connection closed: " + clientSocket.getInetAddress());

        }catch (IOException e){
            System.err.println("[?] Connection error: " + e.getMessage());
        }
    }
}
