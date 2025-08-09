package com.example.chatbot.controllers;

// BitrixBridgeController.java
import com.example.chatbot.bd.ApplicationRepository;
import com.example.chatbot.dto.ApplicationDTO;
import com.fasterxml.jackson.databind.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BitrixBridgeController {
    private final ApplicationRepository repo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${bridge.jwt.secret:change-me-very-strong-secret-change-me}")
    private String jwtSecret;

    // POST /api/b24/mint  — сервером валидируем access_token у Bitrix и выдаём короткий JWT
    @PostMapping("/api/b24/mint")
    public ResponseEntity<?> mint(@RequestBody MintReq req) {
        try {
            String url = "https://" + req.domain + "/rest/user.current?auth=" +
                    URLEncoder.encode(req.accessToken, StandardCharsets.UTF_8);
            var http = HttpClient.newHttpClient();
            var res  = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode()!=200) return ResponseEntity.status(401).body(Map.of("error","bitrix_auth_failed"));

            JsonNode r = om.readTree(res.body()).path("result");
            String uid = r.path("ID").asText("");
            String email = r.path("EMAIL").asText("");
            String username = (r.path("NAME").asText("") + " " + r.path("LAST_NAME").asText("")).trim();
            if (uid.isBlank()) return ResponseEntity.status(401).body(Map.of("error","empty_user_id"));

            var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            String jwt = Jwts.builder()
                    .claim("uid", uid).claim("email", email).claim("username", username)
                    .claim("iss","b24-bridge")
                    .setExpiration(Date.from(Instant.now().plusSeconds(180))) // 3 мин.
                    .signWith(key).compact();

            return ResponseEntity.ok(Map.of("token", jwt));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error","mint_failed","message",e.getMessage()));
        }
    }

    // GET /b24/start — разбираем JWT, кладём в DTO и редиректим в чат
    @GetMapping("/b24/start")
    public ResponseEntity<?> start(@RequestParam String applicationId, @RequestParam String b24) {
        try {
            var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(b24).getBody();

            ApplicationDTO dto = repo.getOrCreate(applicationId);
            dto.setBitrixUserId(String.valueOf(claims.get("uid")));
            dto.setUsername((String) claims.get("username"));
            dto.setUserEmail((String) claims.get("email"));

            String redir = "/index.html?applicationId=" + URLEncoder.encode(applicationId, StandardCharsets.UTF_8);
            System.out.println(dto.getUsername());
            System.out.println(dto.getUserEmail());
            return ResponseEntity.status(302).header("Location", redir).build();
        } catch (Exception e) {
            return ResponseEntity.status(401).body("invalid or expired token");
        }
    }
    @PostMapping("/b24/new")
    public ResponseEntity<?> createFromB24(
            @RequestParam String applicationId,
            @RequestParam String b24) {
        try {
            var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(b24).getBody();

            String uid = String.valueOf(claims.get("uid"));
            String email = (String) claims.get("email");
            String username = (String) claims.get("username");

            ApplicationDTO dto = repo.getOrCreate(applicationId); // новая пустая — мы её сейчас заполним
            if (uid != null && !uid.isBlank()) dto.setBitrixUserId(uid);
            if (username != null && !username.isBlank()) dto.setUsername(username);
            if (email != null && !email.isBlank()) dto.setUserEmail(email);

            return ResponseEntity.ok(Map.of("status","ok"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error","invalid_or_expired_token"));
        }
    }

    public static record MintReq(String accessToken, String domain) {}
}

