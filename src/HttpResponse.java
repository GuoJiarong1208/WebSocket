
import java.util.*;
import java.nio.charset.StandardCharsets;

//模拟构建HTTP响应，设置响应的状态，头部，正文。
public class HttpResponse {
    private int status;//HTTP状态码，也就是静态代码块里的数字
    private String reason;//状态码对应的原因短语
    private final Map<String,String> headers=new LinkedHashMap<>();//存储响应头部信息，linked来保证插入顺序。
    private byte[] body;//存储响应的正文数据

    private static final Map<Integer,String> REASONS=new LinkedHashMap<>();//静态映射表，存储了常见的状态码和原因短语。
    /// 静态代码块里面是数字和响应状态的对应关系
    static {
        REASONS.put(200,"OK");
        REASONS.put(301,"Moved Permanently");
        REASONS.put(302,"Found");
        REASONS.put(404,"Not Found");
        REASONS.put(304,"Not Modified");
        REASONS.put(405,"Method Not Allowed");
        REASONS.put(500,"Internal Server Error");
    }
    public HttpResponse() {
        this.status = 200;
        this.reason = REASONS.get(200);
        this.body = new byte[0];
    }

    public HttpResponse status(int code){
        this.status=code;
        this.reason=REASONS.getOrDefault(code,"Unknown");//查询code对应的状态原因，找不到默认unknown
        return this;//体现链式调用
    }
    public HttpResponse header(String name, String value){//添加或者覆盖一个自定义的响应头
        headers.put(name,value);
        return this;
    }
    public HttpResponse contentType(String mime){//快捷方法，便于设置Content-Type头
        return header("Content-Type",mime);
    }

    public HttpResponse body(String text){
        if(text==null){
            body=new byte[0];
        }else {//设置响应的文本正文，强制使用UTF-8编码
            body=text.getBytes(StandardCharsets.UTF_8);
        }
        return this;
    }
    public HttpResponse bodyBytes(byte[] data){//设置响应的原始字节正文
        body=(data==null)?new byte[0]:data;
        return this;
    }
    public HttpResponse keepAlive(boolean keep){//控制HTTP连接时长连接还是关闭
        header("Connection",keep?"keep-alive":"close");
        return this;
    }
    public HttpResponse redirect(int code,String location){//重定向响应
        if(code!=301&&code!=302) code=302;//检查传入的是不是临时重定向，否则默认302
        status(code);//设置状态码
        header("Location",location);//设置Location头，告诉客户端新的URL跳转地址
        body("<html><body>Redircting to "+location+"</body><>/html>");////////为了提供给用户一个友好的HTML错误提示页面
        return this;
    }
    public HttpResponse notModified(){
        status(304);
        bodyBytes(new byte[0]);//304没有正文
        headers.remove("Content-Type");//移除正文相关的头
        return this;
    }
    private void finalizeHeaders(){
        if(status!=304){
            header("Content-Length",String.valueOf(body.length));//设置正文长度头
        }else {
            header("Content-Length","0");
        }
        if(!headers.containsKey("Date")){
            header("Date",java.time.ZonedDateTime.now().toString());
            //设置当前时间为Date头
        }
        if(!headers.containsKey("Server")){
            header("Server","SimpleJavaHttpServer/1.0");
        }
    }
    public byte[] toBytes(){//把对象形式存储的数据转成HTTP报文，来发给客户端
        finalizeHeaders();
        StringBuilder s=new StringBuilder();
        s.append("HTTP/1.1 ").append(status).append(" ").append(reason).append("\r\n");
        for(Map.Entry<String,String> e:headers.entrySet()){
            s.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        s.append("\r\n");
        byte[] headerBytes=s.toString().getBytes(StandardCharsets.UTF_8);
        if(status==304||body.length==0){
            return headerBytes;
        }
        byte[] all=new byte[headerBytes.length+body.length];
        System.arraycopy(headerBytes,0,all,0,headerBytes.length);
        System.arraycopy(body,0,all,headerBytes.length,body.length);
        return all;
    }
}
