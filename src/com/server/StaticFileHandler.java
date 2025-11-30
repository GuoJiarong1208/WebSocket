package com.server;

import com.http.HttpRequestParser;
import com.http.HttpResponse;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class StaticFileHandler {
    private final File root;

    public StaticFileHandler(File root) {
        this.root = root;
    }

    public HttpResponse handle(HttpRequestParser.HttpRequest req) throws IOException {
        String rawPath = req.getPath();
        String path = URLDecoder.decode(rawPath, "UTF-8");
        File file = new File(root, path).getCanonicalFile();
        if (!file.getPath().startsWith(root.getCanonicalPath())) {
            return new HttpResponse().status(403).contentType("text/plain; charset=utf-8").body("Forbidden");
        }
        if (!file.exists() || file.isDirectory()) {
            return new HttpResponse().status(404).contentType("text/plain; charset=utf-8").body("Not Found");
        }

        long lastModified = file.lastModified();
        String etag = "\"" + lastModified + "-" + file.length() + "\"";

        String ifNoneMatch = req.getHeader("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return new HttpResponse().notModified()
                    .header("ETag", etag)
                    .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME
                            .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.of("GMT"))));
        }

        String ifModifiedSince = req.getHeader("If-Modified-Since");
        if (ifModifiedSince != null) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME);
                long sinceMillis = zdt.toInstant().toEpochMilli();
                if (sinceMillis >= lastModified) {
                    return new HttpResponse().notModified()
                            .header("ETag", etag)
                            .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME
                                    .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.of("GMT"))));
                }
            } catch (Exception ignore) {
                // 解析失败则继续返回完整内容
            }
        }

        byte[] data = Files.readAllBytes(file.toPath());
        String contentType = Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";

        return new HttpResponse()
                .status(200)
                .contentType(contentType)
                .header("ETag", etag)
                .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME
                        .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.of("GMT"))))
                .bodyBytes(data);
    }
}
