package com.bigbread.api;

import com.bigbread.core.DistrictRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/districts")
public class DistrictController {

    public record DistrictResponse(Short id, String code, String name) {
    }

    private final DistrictRepository districtRepository;

    public DistrictController(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @GetMapping
    public List<DistrictResponse> list() {
        return districtRepository.findAll().stream()
                .map(d -> new DistrictResponse(d.getId(), d.getCode(), d.getName()))
                .toList();
    }
}
