package com.hmall.customer.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerPolicyService {
    private final JdbcTemplate db;

    public List<PolicyExcerpt> search(String query, String category) {
        if (query != null && query.length() > 200 || category != null && category.length() > 24)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检索条件过长");
        String normalized = query == null || query.isBlank() ? null : query.trim();
        String pattern = normalized == null ? null : "%" + normalized + "%";
        String selectedCategory = category == null || category.isBlank() ? null : category.trim();
        String sql = "SELECT p.id,p.version,p.title,p.effective_from,p.category,p.excerpt " +
                "FROM customer_policy p WHERE p.status='PUBLISHED' AND p.effective_from<=NOW(6) " +
                "AND (? IS NULL OR p.category=?) " +
                "AND (? IS NULL OR p.title LIKE ? OR p.excerpt LIKE ?) " +
                "AND NOT EXISTS (SELECT 1 FROM customer_policy newer WHERE newer.policy_key=p.policy_key " +
                "AND newer.status='PUBLISHED' AND newer.effective_from<=NOW(6) " +
                "AND (newer.effective_from>p.effective_from OR " +
                "(newer.effective_from=p.effective_from AND newer.version>p.version))) " +
                "ORDER BY p.effective_from DESC,p.id DESC LIMIT 5";
        return db.query(sql, (rs, rowNum) -> {
            PolicyExcerpt excerpt = new PolicyExcerpt();
            excerpt.setPolicyId(rs.getLong("id"));
            excerpt.setVersion(rs.getInt("version"));
            excerpt.setTitle(rs.getString("title"));
            excerpt.setEffectiveFrom(rs.getTimestamp("effective_from").toLocalDateTime());
            excerpt.setCategory(rs.getString("category"));
            excerpt.setExcerpt(rs.getString("excerpt"));
            return excerpt;
        }, selectedCategory, selectedCategory, normalized, pattern, pattern);
    }
}
