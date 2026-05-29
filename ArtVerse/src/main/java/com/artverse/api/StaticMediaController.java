package com.artverse.api;

import com.artverse.config.ArtVerseProperties;
import com.artverse.media.MediaStorageService;
import com.artverse.storage.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/static/manga")
@RequiredArgsConstructor
public class StaticMediaController {

    private final MediaStorageService mediaStorageService;
    private final ObjectStorageService objectStorageService;
    private final ArtVerseProperties properties;

    @GetMapping("/**")
    public void serveImage(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI().substring("/static/manga/".length());
        mediaStorageService.validateImagePath(path);

        Path localPath = mediaStorageService.resolveRelativePath(path);
        if (localPath != null && Files.exists(localPath)) {
            serveLocalFile(localPath, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void serveLocalFile(Path path, HttpServletResponse response) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "image/png";
            response.setContentType(contentType);
            response.setContentLengthLong(Files.size(path));
            try (InputStream in = Files.newInputStream(path);
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            log.warn("Failed to serve file {}: {}", path, e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
