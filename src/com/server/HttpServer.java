package com.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {
    private int port;

    // 线程池
    private ExecutorService threadPool ;

    //构造函数
    public HttpServer(int port){
        this.port=port;
        this.threadPool=Executors.newFixedThreadPool(1);
    }

    public HttpServer(int port, int threadCount){
        this.port=port;
        this.threadPool=Executors.newFixedThreadPool(threadCount);
    }

    public void startServer(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HTTP server is on. Your port is  " + port);

            while (true) {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                System.out.println("[+] New connection from " + clientSocket.getInetAddress());
                // 将连接交给线程池处理
                threadPool.execute(new ConnectionHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[?] Server error: " + e.getMessage());
        }finally{
            threadPool.shutdown();
            System.out.println("[×] Server stopped.");
        }
    }
}
