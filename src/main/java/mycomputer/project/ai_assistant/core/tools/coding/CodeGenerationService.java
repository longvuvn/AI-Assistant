package mycomputer.project.ai_assistant.core.tools.coding;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CodeGenerationService {

    private final Map<String, String> templates = new HashMap<>();

    public CodeGenerationService() {
        initTemplates();
    }

    private void initTemplates() {
        // 1. Mẫu Chuẩn hóa ApiResponse
        templates.put("spring-api-response", """
                package {basePackage}.dto;

                import com.fasterxml.jackson.annotation.JsonInclude;
                import java.time.Instant;

                @JsonInclude(JsonInclude.Include.NON_NULL)
                public record ApiResponse<T>(
                        int code,
                        String message,
                        T data,
                        Instant timestamp
                ) {
                    public static <T> ApiResponse<T> success(T data) {
                        return new ApiResponse<>(200, "Thành công", data, Instant.now());
                    }

                    public static <T> ApiResponse<T> created(T data) {
                        return new ApiResponse<>(201, "Tạo mới thành công", data, Instant.now());
                    }

                    public static <T> ApiResponse<T> error(int code, String message) {
                        return new ApiResponse<>(code, message, null, Instant.now());
                    }
                }
                """);

        // 2. Mẫu Global Exception Handler
        templates.put("spring-exception-handler", """
                package {basePackage}.exception;

                import {basePackage}.dto.ApiResponse;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.validation.FieldError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RestControllerAdvice;

                import java.util.HashMap;
                import java.util.Map;

                @RestControllerAdvice
                public class GlobalExceptionHandler {

                    @ExceptionHandler(MethodArgumentNotValidException.class)
                    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
                        Map<String, String> errors = new HashMap<>();
                        ex.getBindingResult().getAllErrors().forEach(error -> {
                            String fieldName = ((FieldError) error).getField();
                            String errorMessage = error.getDefaultMessage();
                            errors.put(fieldName, errorMessage);
                        });
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(new ApiResponse<>(400, "Dữ liệu đầu vào không hợp lệ", errors, java.time.Instant.now()));
                    }

                    @ExceptionHandler(RuntimeException.class)
                    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(404, ex.getMessage()));
                    }

                    @ExceptionHandler(Exception.class)
                    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(500, "Lỗi máy chủ nội bộ: " + ex.getMessage()));
                    }
                }
                """);

        // 3. Mẫu BaseEntity (Audit timestamps)
        templates.put("spring-base-entity", """
                package {basePackage}.model;

                import jakarta.persistence.*;
                import lombok.Getter;
                import lombok.Setter;
                import org.springframework.data.annotation.CreatedDate;
                import org.springframework.data.annotation.LastModifiedDate;
                import org.springframework.data.jpa.domain.support.AuditingEntityListener;

                import java.time.Instant;

                @MappedSuperclass
                @EntityListeners(AuditingEntityListener.class)
                @Getter
                @Setter
                public abstract class BaseEntity {

                    @CreatedDate
                    @Column(name = "created_at", nullable = false, updatable = false)
                    private Instant createdAt;

                    @LastModifiedDate
                    @Column(name = "updated_at")
                    private Instant updatedAt;
                }
                """);

        // 4. Mẫu Spring Security Configuration (Spring Boot 3+)
        templates.put("spring-security-config", """
                package {basePackage}.config;

                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
                import org.springframework.security.web.SecurityFilterChain;

                @Configuration
                @EnableWebSecurity
                public class SecurityConfig {

                    @Bean
                    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http
                            .csrf(AbstractHttpConfigurer::disable)
                            .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/api/v1/**").permitAll()
                                .anyRequest().authenticated()
                            );
                        return http.build();
                    }
                }
                """);

        // 5. Mẫu Unit Test Mockito Controller & Service
        templates.put("spring-unit-test", """
                package {basePackage}.controller;

                import {basePackage}.model.{Entity};
                import {basePackage}.service.{Entity}Service;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.InjectMocks;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoExtension;
                import org.springframework.http.ResponseEntity;

                import java.util.List;
                import java.util.Optional;

                import static org.junit.jupiter.api.Assertions.*;
                import static org.mockito.Mockito.*;

                @ExtendWith(MockitoExtension.class)
                class {Entity}ControllerTest {

                    @Mock
                    private {Entity}Service service;

                    @InjectMocks
                    private {Entity}Controller controller;

                    @Test
                    void testGetAll_Success() {
                        when(service.getAll()).thenReturn(List.of(new {Entity}()));
                        ResponseEntity<?> response = controller.getAll();
                        assertNotNull(response);
                        assertEquals(200, response.getStatusCode().value());
                        verify(service, times(1)).getAll();
                    }
                }
                """);

        // 6. Thuật toán nâng cao (Graph, UnionFind, Test Assertions)
        templates.put("java-graph", """
                import java.util.*;

                public class Graph {
                    private final int vertices;
                    private final List<List<Edge>> adj;

                    public record Edge(int to, int weight) {}

                    public Graph(int vertices) {
                        this.vertices = vertices;
                        this.adj = new ArrayList<>(vertices);
                        for (int i = 0; i < vertices; i++) adj.add(new ArrayList<>());
                    }

                    public void addEdge(int from, int to, int weight) {
                        adj.get(from).add(new Edge(to, weight));
                    }

                    public List<Edge> getNeighbors(int u) {
                        return adj.get(u);
                    }
                }
                """);

        templates.put("java-unionfind", """
                public class UnionFind {
                    private final int[] parent;
                    private final int[] rank;

                    public UnionFind(int n) {
                        parent = new int[n];
                        rank = new int[n];
                        for (int i = 0; i < n; i++) parent[i] = i;
                    }

                    public int find(int i) {
                        if (parent[i] == i) return i;
                        return parent[i] = find(parent[i]);
                    }

                    public boolean union(int i, int j) {
                        int rootI = find(i);
                        int rootJ = find(j);
                        if (rootI == rootJ) return false;
                        if (rank[rootI] < rank[rootJ]) parent[rootI] = rootJ;
                        else if (rank[rootI] > rank[rootJ]) parent[rootJ] = rootI;
                        else {
                            parent[rootJ] = rootI;
                            rank[rootI]++;
                        }
                        return true;
                    }
                }
                """);

        templates.put("java-test", """
                public class SolutionTest {
                    public static void main(String[] args) {
                        System.out.println("Bắt đầu chạy kiểm thử assertions...");
                        testNormalCase();
                        testEdgeCases();
                        System.out.println("Tất cả kiểm thử đã PASSED!");
                    }

                    static void testNormalCase() {
                        // assert condition : "Error message";
                    }

                    static void testEdgeCases() {
                        // test null, empty, boundary values
                    }
                }
                """);
    }

    /**
     * Lấy mẫu mã nguồn theo tên key
     */
    public String getTemplate(String templateKey, String basePackage, String entityName) {
        String key = templateKey.toLowerCase().trim();
        String content = templates.get(key);
        if (content == null) {
            // Thử format theo kiểu cũ language-category
            return templates.getOrDefault(key, "Không tìm thấy mẫu cho key '" + templateKey + "'. Danh sách mẫu hỗ trợ: " + String.join(", ", templates.keySet()));
        }

        String pkg = (basePackage != null && !basePackage.isBlank()) ? basePackage : "com.example.demo";
        String ent = (entityName != null && !entityName.isBlank()) ? entityName : "Entity";

        return content.replace("{basePackage}", pkg).replace("{Entity}", ent);
    }

    /**
     * Tự động sinh trọn bộ khung CRUD Spring Boot 3+ (Entity, DTO, Repository, Service, Controller)
     */
    public Map<String, String> generateSpringCrudScaffold(String basePackage, String entityName, String fieldsSpec) {
        Map<String, String> generatedFiles = new LinkedHashMap<>();

        String pkg = (basePackage != null && !basePackage.isBlank()) ? basePackage : "com.example.demo";
        String entity = capitalize(entityName != null ? entityName : "Item");
        String variableName = Character.toLowerCase(entity.charAt(0)) + entity.substring(1);
        String endpointPath = "/api/v1/" + variableName + "s";

        List<FieldDef> fields = parseFields(fieldsSpec);

        // 1. Entity
        String entityCode = buildEntityCode(pkg, entity, fields);
        generatedFiles.put("model/" + entity + ".java", entityCode);

        // 2. DTOs
        String requestDtoCode = buildRequestDtoCode(pkg, entity, fields);
        generatedFiles.put("dto/" + entity + "Request.java", requestDtoCode);

        String responseDtoCode = buildResponseDtoCode(pkg, entity, fields);
        generatedFiles.put("dto/" + entity + "Response.java", responseDtoCode);

        // 3. Repository
        String repoCode = buildRepositoryCode(pkg, entity);
        generatedFiles.put("repository/" + entity + "Repository.java", repoCode);

        // 4. Service & Impl
        String serviceCode = buildServiceCode(pkg, entity);
        generatedFiles.put("service/" + entity + "Service.java", serviceCode);

        String serviceImplCode = buildServiceImplCode(pkg, entity, variableName, fields);
        generatedFiles.put("service/impl/" + entity + "ServiceImpl.java", serviceImplCode);

        // 5. Controller
        String controllerCode = buildControllerCode(pkg, entity, variableName, endpointPath);
        generatedFiles.put("controller/" + entity + "Controller.java", controllerCode);

        return generatedFiles;
    }

    private record FieldDef(String type, String name) {}

    private List<FieldDef> parseFields(String fieldsSpec) {
        List<FieldDef> list = new ArrayList<>();
        if (fieldsSpec == null || fieldsSpec.isBlank()) {
            list.add(new FieldDef("String", "name"));
            list.add(new FieldDef("String", "description"));
            return list;
        }

        String[] parts = fieldsSpec.split("[,;]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length >= 2) {
                list.add(new FieldDef(tokens[0], tokens[1]));
            } else if (tokens.length == 1) {
                list.add(new FieldDef("String", tokens[0]));
            }
        }
        return list.isEmpty() ? List.of(new FieldDef("String", "name")) : list;
    }

    private String buildEntityCode(String pkg, String entity, List<FieldDef> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".model;\n\n")
                .append("import jakarta.persistence.*;\n")
                .append("import lombok.*;\n\n")
                .append("@Entity\n")
                .append("@Table(name = \"").append(entity.toLowerCase()).append("s\")\n")
                .append("@Data\n")
                .append("@NoArgsConstructor\n")
                .append("@AllArgsConstructor\n")
                .append("@Builder\n")
                .append("public class ").append(entity).append(" {\n\n")
                .append("    @Id\n")
                .append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n")
                .append("    private Long id;\n\n");

        for (FieldDef f : fields) {
            sb.append("    private ").append(f.type()).append(" ").append(f.name()).append(";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildRequestDtoCode(String pkg, String entity, List<FieldDef> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".dto;\n\n")
                .append("import jakarta.validation.constraints.*;\n\n")
                .append("public record ").append(entity).append("Request(\n");

        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            String annotation = f.type().equals("String") ? "    @NotBlank(message = \"" + f.name() + " không được để trống\") "
                    : "    @NotNull(message = \"" + f.name() + " không được null\") ";
            sb.append(annotation).append(f.type()).append(" ").append(f.name());
            if (i < fields.size() - 1) sb.append(",\n");
        }

        sb.append("\n) {}\n");
        return sb.toString();
    }

    private String buildResponseDtoCode(String pkg, String entity, List<FieldDef> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".dto;\n\n")
                .append("import ").append(pkg).append(".model.").append(entity).append(";\n\n")
                .append("public record ").append(entity).append("Response(\n")
                .append("    Long id,\n");

        for (int i = 0; i < fields.size(); i++) {
            FieldDef f = fields.get(i);
            sb.append("    ").append(f.type()).append(" ").append(f.name());
            if (i < fields.size() - 1) sb.append(",\n");
        }

        sb.append("\n) {\n")
                .append("    public static ").append(entity).append("Response from(").append(entity).append(" entity) {\n")
                .append("        return new ").append(entity).append("Response(\n")
                .append("            entity.getId()");

        for (FieldDef f : fields) {
            String getter = "entity.get" + capitalize(f.name()) + "()";
            sb.append(",\n            ").append(getter);
        }

        sb.append("\n        );\n    }\n}\n");
        return sb.toString();
    }

    private String buildRepositoryCode(String pkg, String entity) {
        return String.format("""
                package %s.repository;

                import %s.model.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                public interface %sRepository extends JpaRepository<%s, Long> {
                }
                """, pkg, pkg, entity, entity, entity);
    }

    private String buildServiceCode(String pkg, String entity) {
        return String.format("""
                package %s.service;

                import %s.dto.%sRequest;
                import %s.dto.%sResponse;
                import java.util.List;

                public interface %sService {
                    List<%sResponse> getAll();
                    %sResponse getById(Long id);
                    %sResponse create(%sRequest request);
                    %sResponse update(Long id, %sRequest request);
                    void delete(Long id);
                }
                """, pkg, pkg, entity, pkg, entity, entity, entity, entity, entity, entity, entity, entity, entity);
    }

    private String buildServiceImplCode(String pkg, String entity, String varName, List<FieldDef> fields) {
        StringBuilder setterBuilder = new StringBuilder();
        for (FieldDef f : fields) {
            String cap = capitalize(f.name());
            setterBuilder.append("        existing.set").append(cap).append("(request.").append(f.name()).append("());\n");
        }

        StringBuilder entityBuild = new StringBuilder();
        entityBuild.append(entity).append(".builder()\n");
        for (FieldDef f : fields) {
            entityBuild.append("                .").append(f.name()).append("(request.").append(f.name()).append("())\n");
        }
        entityBuild.append("                .build()");

        return String.format("""
                package %s.service.impl;

                import %s.dto.%sRequest;
                import %s.dto.%sResponse;
                import %s.model.%s;
                import %s.repository.%sRepository;
                import %s.service.%sService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                import java.util.List;

                @Service
                @RequiredArgsConstructor
                @Transactional
                public class %sServiceImpl implements %sService {

                    private final %sRepository %sRepository;

                    @Override
                    @Transactional(readOnly = true)
                    public List<%sResponse> getAll() {
                        return %sRepository.findAll().stream()
                                .map(%sResponse::from)
                                .toList();
                    }

                    @Override
                    @Transactional(readOnly = true)
                    public %sResponse getById(Long id) {
                        %s entity = %sRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("%s không tồn tại với id: " + id));
                        return %sResponse.from(entity);
                    }

                    @Override
                    public %sResponse create(%sRequest request) {
                        %s entity = %s;
                        %s saved = %sRepository.save(entity);
                        return %sResponse.from(saved);
                    }

                    @Override
                    public %sResponse update(Long id, %sRequest request) {
                        %s existing = %sRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("%s không tồn tại với id: " + id));
                %s
                        %s updated = %sRepository.save(existing);
                        return %sResponse.from(updated);
                    }

                    @Override
                    public void delete(Long id) {
                        if (!%sRepository.existsById(id)) {
                            throw new RuntimeException("%s không tồn tại với id: " + id);
                        }
                        %sRepository.deleteById(id);
                    }
                }
                """,
                pkg, pkg, entity, pkg, entity, pkg, entity, pkg, entity, pkg, entity,
                entity, entity,
                entity, varName,
                entity, varName, entity,
                entity, entity, varName, entity, entity,
                entity, entity, entity, entityBuild, entity, varName, entity,
                entity, entity, entity, varName, entity, setterBuilder, entity, varName, entity,
                varName, entity, varName
        );
    }

    private String buildControllerCode(String pkg, String entity, String varName, String endpointPath) {
        return String.format("""
                package %s.controller;

                import %s.dto.%sRequest;
                import %s.dto.%sResponse;
                import %s.service.%sService;
                import jakarta.validation.Valid;
                import lombok.RequiredArgsConstructor;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                import java.util.List;

                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService %sService;

                    @GetMapping
                    public ResponseEntity<List<%sResponse>> getAll() {
                        return ResponseEntity.ok(%sService.getAll());
                    }

                    @GetMapping("/{id}")
                    public ResponseEntity<%sResponse> getById(@PathVariable Long id) {
                        return ResponseEntity.ok(%sService.getById(id));
                    }

                    @PostMapping
                    public ResponseEntity<%sResponse> create(@Valid @RequestBody %sRequest request) {
                        return ResponseEntity.status(HttpStatus.CREATED).body(%sService.create(request));
                    }

                    @PutMapping("/{id}")
                    public ResponseEntity<%sResponse> update(@PathVariable Long id, @Valid @RequestBody %sRequest request) {
                        return ResponseEntity.ok(%sService.update(id, request));
                    }

                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable Long id) {
                        %sService.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                }
                """,
                pkg, pkg, entity, pkg, entity, pkg, entity,
                endpointPath, entity, entity, varName,
                entity, varName,
                entity, varName,
                entity, entity, varName,
                entity, entity, varName,
                varName
        );
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
