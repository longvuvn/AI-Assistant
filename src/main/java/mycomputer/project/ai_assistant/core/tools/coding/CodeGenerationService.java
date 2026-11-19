package mycomputer.project.ai_assistant.core.tools.coding;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CodeGenerationService {

    private final Map<String, String> templates = new HashMap<>();

    public CodeGenerationService() {
        templates.put("java-graph", """
                // Graph structure using Adjacency List
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
                // Unit test assertion pattern
                public class SolutionTest {
                    public static void main(String[] args) {
                        System.out.println("Bắt đầu chạy kiểm thử...");
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

    public String getTemplate(String language, String category) {
        String key = (language + "-" + category).toLowerCase();
        return templates.getOrDefault(key,
                "Không có sẵn mẫu cho '" + key + "'. Agent hãy tự thiết kế mã tối ưu theo chuẩn Master/Thạc sĩ.");
    }
}
