package com.aquagreen.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url:https://placeholder.supabase.co}")
    private String supabaseUrl;

    @Value("${supabase.key:placeholder-key}")
    private String supabaseKey;

    @Value("${supabase.bucket:aga-images}")
    private String bucket;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Upload a file to Supabase Storage.
     * Returns the public URL of the uploaded file.
     * Falls back gracefully if Supabase is not configured.
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (supabaseUrl.contains("placeholder")) {
            log.warn("Supabase not configured — skipping upload for {}", file.getOriginalFilename());
            return null;
        }

        try {
            String ext = getExtension(file.getOriginalFilename());
            String fileName = folder + "/" + UUID.randomUUID() + "." + ext;
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + fileName;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("apikey", supabaseKey);
            headers.setContentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
            headers.set("x-upsert", "true");

            HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
                log.info("Uploaded {} to Supabase: {}", file.getOriginalFilename(), publicUrl);
                return publicUrl;
            }
        } catch (Exception e) {
            log.error("Supabase upload failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Delete a file from Supabase Storage by its public URL.
     */
    public boolean deleteFile(String publicUrl) {
        if (publicUrl == null || supabaseUrl.contains("placeholder")) return false;
        try {
            String path = publicUrl.replace(supabaseUrl + "/storage/v1/object/public/" + bucket + "/", "");
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("apikey", supabaseKey);
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            return true;
        } catch (Exception e) {
            log.warn("Supabase delete failed: {}", e.getMessage());
            return false;
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1).toLowerCase() : "jpg";
    }
}
