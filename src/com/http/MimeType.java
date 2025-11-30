package com.http;

public enum MimeType {
    TEXT_PLAIN("text/plain"),
    TEXT_HTML("text/html"),
    IMAGE_PNG("image/png"),
    APPLICATION_JSON("application/json"),//先列出来了三个枚举基本数据类型，文本，图片和网页。
    IMAGE_JPG("image/jpg");


    private final String type;

    MimeType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static String fromFilename(String filename){
        if(filename==null) return TEXT_PLAIN.type;//空返回文本
        String lower=filename.toLowerCase();
        if(lower.endsWith(".html")||lower.endsWith(".htm")){
            return TEXT_HTML.type;
        }else if(lower.endsWith(".png")){
            return IMAGE_PNG.type;
        }else if(lower.endsWith(".json")){
            return APPLICATION_JSON.type;
        }else if(lower.endsWith(".jpg")||lower.endsWith(".jpeg")){
            return IMAGE_JPG.type;
        }else{
            return TEXT_PLAIN.type;
        }
    }
}

