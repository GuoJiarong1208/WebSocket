package com.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.*;
import java.io.IOException;

public class HttpRequestParser {

    public static class HttpRequest{
        private final String method;
        private final String path;
        private final String version;
        private final Map<String, String> headers;
        private final String body;

        HttpRequest(String method, String path, String version, Map<String, String> headers, String body){
            this.method=method;
            this.path=path;
            this.version=version;
            this.headers=headers;
            this.body=body;
        }

        public String getMethod() {return method;}
        public String getPath(){return path;}
        public String getVersion(){return version;}
        public String getBody(){return body;}
        public String getHeader(String name){return headers.getOrDefault(name.toLowerCase(),null);}//找到返回对应值，没找到返回空

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);////这是为什么
        }
        //是否长连接的判断
        public boolean isKeepAlive(){
            String c=getHeader("connection");/////这是和什么搭配起来的，没看懂
            return c!=null&&c.equalsIgnoreCase("keep-alive");
        }
    }

    //下面是解析请求的函数，阻塞直到读到空行，按照Content-Length再读体
    public HttpRequest parse(InputStream in,String body1) throws IOException{

        BufferedReader reader=new BufferedReader(new InputStreamReader(in));
        String startLine=reader.readLine();
        if(startLine==null||startLine.isEmpty()) return null;

        String[] parts=startLine.split("\\s+");//用任意数量的空格来划分字符串

        String method=parts[0];
        String path=parts[1];
        String version=parts[2];

        Map<String,String> headers=new LinkedHashMap<>();
        String line;

        //下面开始读取
        while ((line=reader.readLine())!=null&&!line.isEmpty()){
            int index=line.indexOf(':');
            if(index>0){
                String name=line.substring(0,index).trim().toLowerCase();/////trim()用来去掉前后空格
                String value=line.substring(index+1).trim();
                headers.put(name,value);
            }
        }

        String body=body1;
//        String cl=headers.get("content-length");
//        if(cl!=null){
//            int len;
//            try{ len=Integer.parseInt(cl);}
//            catch (NumberFormatException e){ len=0;}
//            if(len>0){
//                char[] buf=new char[len];
//                int off=0;
//                while (off<len){
//                    int r=reader.read(buf,off,len-off);
//                    if(r==-1) break;
//                    off+=r;
//                }
//                body=new String(buf,0,off);
//            }
//        }

        return new HttpRequest(method,path,version,headers,body);
    }
}
