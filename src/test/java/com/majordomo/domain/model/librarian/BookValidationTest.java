package com.majordomo.domain.model.librarian;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Book validBook() {
        var book = new Book();
        book.setTitle("Refactoring");
        return book;
    }

    @Test
    @DisplayName("A rating above 5 is rejected — the scale is 1 to 5")
    void shouldRejectRatingWhenAboveFive() {
        var book = validBook();
        book.setRating(9);

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rating");
    }

    @Test
    @DisplayName("A rating below 1 is rejected — zero is not a rating, absence is")
    void shouldRejectRatingWhenBelowOne() {
        var book = validBook();
        book.setRating(0);

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rating");
    }

    @Test
    @DisplayName("Ratings 1 through 5 are all accepted")
    void shouldAcceptRatingWhenWithinScale() {
        for (int r = 1; r <= 5; r++) {
            var book = validBook();
            book.setRating(r);

            assertThat(validator.validate(book))
                    .as("rating %d should be valid", r)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("A blank title is rejected — a book with no title cannot be deduped or matched")
    void shouldRejectTitleWhenBlank() {
        var book = new Book();
        book.setTitle("   ");

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title");
    }
}
