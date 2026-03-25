package com.majordomo.adapter.in.web.steward;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for bulk CSV import of properties in the Steward domain.
 *
 * <p>Parses an uploaded CSV file and delegates property creation to
 * {@link ManagePropertyUseCase#create(Property)}. CSV parsing is an adapter-layer
 * concern, keeping the domain free of file-format knowledge.</p>
 */
@RestController
@RequestMapping("/api/properties")
@Tag(name = "Steward", description = "Property management")
public class PropertyImportController {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyImportController.class);

    private final ManagePropertyUseCase propertyUseCase;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param propertyUseCase           the inbound port for property management
     * @param organizationAccessService the service for verifying organization membership
     */
    public PropertyImportController(ManagePropertyUseCase propertyUseCase,
                                    OrganizationAccessService organizationAccessService) {
        this.propertyUseCase = propertyUseCase;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Imports properties from a CSV file upload.
     *
     * <p>Expected CSV columns (header row required):
     * {@code name,description,serialNumber,modelNumber,manufacturer,category,location,
     * status,acquiredOn,warrantyExpiresOn,purchasePrice}. Only {@code name} is required;
     * rows missing a name are skipped and reported as errors.</p>
     *
     * @param organizationId the target organization UUID
     * @param file           the CSV file to import
     * @return the import result with counts and per-row errors
     * @throws IOException if the file cannot be read
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importCsv(
            @RequestParam UUID organizationId,
            @RequestParam("file") MultipartFile file) throws IOException {
        organizationAccessService.verifyAccess(organizationId);

        List<Map<String, String>> rows = parseCsv(file);
        ImportResult result = processRows(organizationId, rows);
        return ResponseEntity.ok(result);
    }

    /**
     * Parses a CSV file into a list of field maps keyed by header column names.
     *
     * @param file the uploaded CSV file
     * @return list of row maps (one per data row)
     * @throws IOException if reading fails
     */
    List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            String[] headers = headerLine.split(",", -1);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = i < values.length ? values[i].trim() : "";
                    row.put(headers[i], value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Processes parsed CSV rows, creating properties and collecting errors.
     *
     * @param organizationId the target organization
     * @param rows           the parsed CSV rows
     * @return the import result
     */
    ImportResult processRows(UUID organizationId, List<Map<String, String>> rows) {
        int created = 0;
        int skipped = 0;
        List<ImportResult.ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 1;
            Map<String, String> row = rows.get(i);

            String name = row.getOrDefault("name", "").trim();
            if (name.isEmpty()) {
                errors.add(new ImportResult.ImportError(rowNum, "Missing required field: name"));
                skipped++;
                continue;
            }

            try {
                Property property = mapRowToProperty(organizationId, row);
                propertyUseCase.create(property);
                created++;
            } catch (IllegalArgumentException e) {
                errors.add(new ImportResult.ImportError(rowNum, e.getMessage()));
                skipped++;
            }
        }

        LOG.info("CSV import complete for org {}: total={}, created={}, skipped={}",
                organizationId, rows.size(), created, skipped);

        return new ImportResult(rows.size(), created, skipped, errors);
    }

    private Property mapRowToProperty(UUID organizationId, Map<String, String> row) {
        Property property = new Property();
        property.setOrganizationId(organizationId);
        property.setName(row.getOrDefault("name", "").trim());
        property.setDescription(blankToNull(row.get("description")));
        property.setSerialNumber(blankToNull(row.get("serialNumber")));
        property.setModelNumber(blankToNull(row.get("modelNumber")));
        property.setManufacturer(blankToNull(row.get("manufacturer")));
        property.setCategory(blankToNull(row.get("category")));
        property.setLocation(blankToNull(row.get("location")));

        String statusStr = blankToNull(row.get("status"));
        if (statusStr != null) {
            try {
                property.setStatus(PropertyStatus.valueOf(statusStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + statusStr);
            }
        }

        String acquiredOn = blankToNull(row.get("acquiredOn"));
        if (acquiredOn != null) {
            try {
                property.setAcquiredOn(LocalDate.parse(acquiredOn));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid acquiredOn date: " + acquiredOn);
            }
        }

        String warrantyExpires = blankToNull(row.get("warrantyExpiresOn"));
        if (warrantyExpires != null) {
            try {
                property.setWarrantyExpiresOn(LocalDate.parse(warrantyExpires));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid warrantyExpiresOn date: " + warrantyExpires);
            }
        }

        String priceStr = blankToNull(row.get("purchasePrice"));
        if (priceStr != null) {
            try {
                property.setPurchasePrice(new BigDecimal(priceStr));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid purchasePrice: " + priceStr);
            }
        }

        return property;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
