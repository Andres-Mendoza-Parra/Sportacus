package com.sportacus.backend.catalog;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sportacus.backend.catalog.dto.EquipmentResponse;

/** Endpoints de solo lectura sobre el catálogo (equipment, activity, etc.). */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final JdbcTemplate jdbcTemplate;

    public CatalogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/equipment")
    public List<EquipmentResponse> listEquipment() {
        String sql = "SELECT id, name, category FROM sportacus.equipment ORDER BY category, name";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EquipmentResponse(
                rs.getString("id"), rs.getString("name"), rs.getString("category")));
    }
}
