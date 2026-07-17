package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final SupabaseStorageService storageService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;
    
    //check
    @Value("${app.upload.base-url:https://api.aquagreenagencies.com/uploads}")
    private String uploadBaseUrl;

    @Value("${supabase.url:https://placeholder.supabase.co}")
    private String supabaseUrl;

    private static final long MAX_SIZE = 10L * 1024 * 1024; // 10 MB

    /**
     * Upload an image from the device (multipart file).
     * Strategy:
     * 1. If Supabase is configured → upload to Supabase, return public URL
     * 2. Otherwise → save to local ./uploads/<folder>/ directory,
     * return http://localhost:8080/uploads/<folder>/filename.ext
     *
     * The returned URL is stored in the database (imageUrl field).
     * In production with Supabase, this is a CDN URL.
     * In local dev, it's the local server URL which works fine for the admin panel.
     */
    @PostMapping("/image")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        if (file.isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error("No file selected"));

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only image files are allowed (JPG, PNG, WebP, GIF)"));

        if (file.getSize() > MAX_SIZE)
            return ResponseEntity.badRequest().body(ApiResponse.error("File is too large. Maximum size is 10 MB"));

        // ── Try Supabase first ──────────────────────────────────
        if (!supabaseUrl.contains("placeholder")) {
            String url = storageService.uploadFile(file, folder);
            if (url != null) {
                log.info("Uploaded to Supabase: {}", url);
                return ResponseEntity.ok(ApiResponse.success("Uploaded to Supabase",
                        Map.of("url", url, "filename", safe(file.getOriginalFilename()), "storage", "supabase")));
            }
        }

        // ── Fall back to local disk storage ────────────────────
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

            // Sanitise folder name — only allow alphanumeric + dash/underscore
            String safeFolder = folder.replaceAll("[^a-zA-Z0-9_-]", "");
            Path dir = Paths.get(uploadDir, safeFolder).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            Path dest = dir.resolve(filename);
            file.transferTo(dest.toFile());

            String publicUrl = uploadBaseUrl.replaceAll("/$", "")
                    + "/" + safeFolder + "/" + filename;

            log.info("Saved locally: {} → {}", dest, publicUrl);
            return ResponseEntity.ok(ApiResponse.success("Uploaded to local storage",
                    Map.of("url", publicUrl, "filename", filename, "storage", "local")));

        } catch (IOException e) {
            log.error("Local save failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(
                    ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    /**
     * Save an image from a public URL — fetches the remote image and stores it
     * locally.
     * Useful when copying from Unsplash or other sources to keep images
     * self-hosted.
     */
    @PostMapping("/image-from-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFromUrl(
            @RequestBody Map<String, String> body) {

        String sourceUrl = body.get("url");
        String folder = body.getOrDefault("folder", "general");

        if (sourceUrl == null || sourceUrl.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("url is required"));

        // Basic URL validation
        if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://"))
            return ResponseEntity.badRequest().body(ApiResponse.error("URL must start with http:// or https://"));

        try {
            java.net.URL url = new java.net.URL(sourceUrl);
            java.net.URLConnection conn = url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "AGA-Image-Fetcher/1.0");

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/"))
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("The URL does not point to an image file"));

            byte[] bytes = conn.getInputStream().readAllBytes();
            if (bytes.length > MAX_SIZE)
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Remote image is too large (max 10 MB)"));

            // Detect extension from content type
            String ext = switch (contentType.toLowerCase()) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                default -> "jpg";
            };

            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            String safeFolder = folder.replaceAll("[^a-zA-Z0-9_-]", "");
            Path dir = Paths.get(uploadDir, safeFolder).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);

            String publicUrl = uploadBaseUrl.replaceAll("/$", "")
                    + "/" + safeFolder + "/" + filename;

            log.info("Fetched URL {} → saved locally as {}", sourceUrl, publicUrl);
            return ResponseEntity.ok(ApiResponse.success("Image saved from URL",
                    Map.of("url", publicUrl, "filename", filename, "storage", "local", "sourceUrl", sourceUrl)));

        } catch (Exception e) {
            log.error("URL fetch failed for {}: {}", sourceUrl, e.getMessage());
            return ResponseEntity.status(500).body(
                    ApiResponse.error("Could not fetch image from URL: " + e.getMessage()));
        }
    }

    /**
     * Delete a locally stored image by its public URL.
     * Supabase deletions are also forwarded to the storage service.
     */
    @DeleteMapping("/image")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("url is required"));

        if (url.contains("/uploads/")) {
            // Local file — derive path and delete
            try {
                String relative = url.substring(url.indexOf("/uploads/") + "/uploads/".length());
                Path filePath = Paths.get(uploadDir, relative).toAbsolutePath().normalize();
                // Security check — must be inside upload dir
                if (filePath.startsWith(Paths.get(uploadDir).toAbsolutePath())) {
                    Files.deleteIfExists(filePath);
                    log.info("Deleted local file: {}", filePath);
                }
            } catch (IOException e) {
                log.warn("Delete failed: {}", e.getMessage());
            }
        } else {
            storageService.deleteFile(url);
        }
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    private String getExtension(String filename) {
        if (filename == null)
            return "jpg";
        int i = filename.lastIndexOf('.');
        return i >= 0 ? filename.substring(i + 1).toLowerCase() : "jpg";
    }

    private String safe(String s) {
        return s != null ? s : "file";
    }

}
