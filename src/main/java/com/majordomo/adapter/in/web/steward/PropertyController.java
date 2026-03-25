package com.majordomo.adapter.in.web.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyRepository propertyRepository;

    public PropertyController(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @GetMapping
    public List<Property> listByOrganization(@RequestParam UUID organizationId) {
        return propertyRepository.findByOrganizationId(organizationId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Property> getById(@PathVariable UUID id) {
        return propertyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    public List<Property> getChildren(@PathVariable UUID id) {
        return propertyRepository.findByParentId(id);
    }

    @PostMapping
    public ResponseEntity<Property> create(@RequestBody Property property) {
        property.setId(UUID.randomUUID());
        if (property.getStatus() == null) {
            property.setStatus(PropertyStatus.ACTIVE);
        }
        property.setCreatedAt(Instant.now());
        property.setUpdatedAt(Instant.now());
        var saved = propertyRepository.save(property);
        return ResponseEntity.created(URI.create("/api/properties/" + saved.getId())).body(saved);
    }
}
