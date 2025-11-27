后面如果用到响应的各部分，关注那几个get函数即可。
如果有看不懂的请把代码喂给AI。。。
第三步可能还需要在ConnectionHandler.java里面继续完善.

#### 主题：基于Java Socket API搭建简单的HTTP客户端和服务器端程序

说明：

1. 不允许基于netty等框架，完全基于Java Socket API进行编写

2. 不分区使用的IO模型，BIO、NIO和AIO都可以

3. 实现基础的HTTP请求、响应功能，具体要求如下：

   1. HTTP客户端可以发送请求报文、呈现响应报文（命令行和GUI都可以）
   2. HTTP客户端对301、302、304的状态码做相应的处理
   3. HTTP服务器端支持GET和POST请求
   4. HTTP服务器端支持200、301、302、304、404、405、500的状态码
   5. HTTP服务器端实现长连接
   6. MIME至少支持三种类型，包含一种非文本类型

4. 基于以上的要求，实现注册，登录功能(数据无需持久化，存在内存中即可，只需要实现注册和登录的接口，可以使用postman等方法模拟请求发送，无需客户端)。


| 角色                     | 负责内容                                                     | 产出文件                                  |
| ------------------------ | ------------------------------------------------------------ | ----------------------------------------- |
| A：服务器基础 & I/O框架  | 建立ServerSocket、接收连接、读取请求、实现长连接机制         | HttpServer.java<br>ConnectionHandler.java |
| B：HTTP协议解析 & 响应构造 | 解析请求行/头/体；构造响应行/头；处理MIME & 状态码（200/301/302/304/404/405/500） | HttpRequestParser.java<br>HttpResponse.java<br>MimeType.java |
| C：业务接口（注册/登录）与路由 | HashMap存用户；实现/register /login POST处理；实现重定向；路由路径分发 | Router.java<br>UserService.java<br>UserController.java |
| D：客户端 + 文档 + 演示  | 用Socket手写GET/POST请求，打印响应，支持302自动重定向；编写最终报告 + 演示流程 | HttpClient.java + 说明文档 + PPT          |