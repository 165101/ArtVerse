package com.artverse.api;

import com.artverse.application.ExportImportService;
import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.Story;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExportImportController {

    private final ExportImportService exportImportService;
    private final ArtVerseProperties properties;

    @GetMapping("/stories/{storyId}/export")
    public ResponseEntity<byte[]> exportStory(@PathVariable Long storyId) {
        byte[] zipData = exportImportService.exportStory(storyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=story_" + storyId + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipData);
    }

    @PostMapping("/stories/import")
    public Story importStory(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > properties.getImportConfig().getMaxZipBytes()) {
            throw new BusinessException(413, "Zip file exceeds max size");
        }
        return exportImportService.importStory(file.getBytes());
    }
}
