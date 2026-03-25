package com.majordomo.adapter.in.web.steward;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyImportControllerTest {

    @Mock
    private ManagePropertyUseCase propertyUseCase;

    @Mock
    private OrganizationAccessService organizationAccessService;

    private PropertyImportController controller;

    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new PropertyImportController(propertyUseCase, organizationAccessService);
    }

    @Test
    void parseCsvReturnsRowMaps() throws Exception {
        String csv = "name,description,category\nDesk,A wooden desk,Furniture\nChair,,Furniture\n";
        MockMultipartFile file = csvFile(csv);

        List<Map<String, String>> rows = controller.parseCsv(file);

        assertEquals(2, rows.size());
        assertEquals("Desk", rows.get(0).get("name"));
        assertEquals("A wooden desk", rows.get(0).get("description"));
        assertEquals("Chair", rows.get(1).get("name"));
        assertEquals("", rows.get(1).get("description"));
    }

    @Test
    void parseCsvSkipsBlankLines() throws Exception {
        String csv = "name\nDesk\n\nChair\n";
        MockMultipartFile file = csvFile(csv);

        List<Map<String, String>> rows = controller.parseCsv(file);

        assertEquals(2, rows.size());
    }

    @Test
    void parseCsvEmptyFileReturnsEmptyList() throws Exception {
        MockMultipartFile file = csvFile("");

        List<Map<String, String>> rows = controller.parseCsv(file);

        assertEquals(0, rows.size());
    }

    @Test
    void processRowsCreatesPropertyForValidRow() {
        when(propertyUseCase.create(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, String>> rows = List.of(
                Map.of("name", "Desk", "category", "Furniture")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(1, result.total());
        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertEquals(0, result.errors().size());

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyUseCase).create(captor.capture());
        assertEquals("Desk", captor.getValue().getName());
        assertEquals("Furniture", captor.getValue().getCategory());
        assertEquals(ORG_ID, captor.getValue().getOrganizationId());
    }

    @Test
    void processRowsSkipsRowMissingName() {
        List<Map<String, String>> rows = List.of(
                Map.of("name", "", "category", "Furniture")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(1, result.total());
        assertEquals(0, result.created());
        assertEquals(1, result.skipped());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.errors().get(0).row());
        assertEquals("Missing required field: name", result.errors().get(0).message());
        verify(propertyUseCase, never()).create(any());
    }

    @Test
    void processRowsSetsStatusFromCsv() {
        when(propertyUseCase.create(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, String>> rows = List.of(
                Map.of("name", "Laptop", "status", "IN_SERVICE")
        );

        controller.processRows(ORG_ID, rows);

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyUseCase).create(captor.capture());
        assertEquals(PropertyStatus.IN_SERVICE, captor.getValue().getStatus());
    }

    @Test
    void processRowsReportsInvalidStatus() {
        List<Map<String, String>> rows = List.of(
                Map.of("name", "Laptop", "status", "BOGUS")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(1, result.skipped());
        assertEquals("Invalid status: BOGUS", result.errors().get(0).message());
    }

    @Test
    void processRowsParsesDatesAndPrice() {
        when(propertyUseCase.create(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, String>> rows = List.of(
                Map.of("name", "Laptop",
                        "acquiredOn", "2025-06-15",
                        "warrantyExpiresOn", "2027-06-15",
                        "purchasePrice", "1299.99")
        );

        controller.processRows(ORG_ID, rows);

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyUseCase).create(captor.capture());
        Property created = captor.getValue();
        assertEquals(LocalDate.of(2025, 6, 15), created.getAcquiredOn());
        assertEquals(LocalDate.of(2027, 6, 15), created.getWarrantyExpiresOn());
        assertEquals(new BigDecimal("1299.99"), created.getPurchasePrice());
    }

    @Test
    void processRowsReportsInvalidDate() {
        List<Map<String, String>> rows = List.of(
                Map.of("name", "Desk", "acquiredOn", "not-a-date")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(1, result.skipped());
        assertEquals("Invalid acquiredOn date: not-a-date", result.errors().get(0).message());
    }

    @Test
    void processRowsReportsInvalidPrice() {
        List<Map<String, String>> rows = List.of(
                Map.of("name", "Desk", "purchasePrice", "abc")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(1, result.skipped());
        assertEquals("Invalid purchasePrice: abc", result.errors().get(0).message());
    }

    @Test
    void processRowsMixedValidAndInvalid() {
        when(propertyUseCase.create(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, String>> rows = List.of(
                Map.of("name", "Desk"),
                Map.of("name", ""),
                Map.of("name", "Chair", "status", "BAD_STATUS"),
                Map.of("name", "Lamp")
        );

        ImportResult result = controller.processRows(ORG_ID, rows);

        assertEquals(4, result.total());
        assertEquals(2, result.created());
        assertEquals(2, result.skipped());
        assertEquals(2, result.errors().size());
        verify(propertyUseCase, times(2)).create(any());
    }

    @Test
    void processRowsLeavesOptionalFieldsNull() {
        when(propertyUseCase.create(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, String>> rows = List.of(
                Map.of("name", "Desk")
        );

        controller.processRows(ORG_ID, rows);

        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyUseCase).create(captor.capture());
        Property created = captor.getValue();
        assertNull(created.getDescription());
        assertNull(created.getSerialNumber());
        assertNull(created.getStatus());
        assertNull(created.getAcquiredOn());
        assertNull(created.getPurchasePrice());
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "import.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
