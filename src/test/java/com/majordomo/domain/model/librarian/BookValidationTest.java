package com.majordomo.domain.model.librarian;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    void book_rejectsRatingAboveFive() {
        var book = validBook();
        book.setRating(9);

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rating");
    }

    @Test
    void book_rejectsRatingBelowOne() {
        var book = validBook();
        book.setRating(0);

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("rating");
    }

    @Test
    void book_acceptsRatingsOneThroughFive() {
        for (int r = 1; r <= 5; r++) {
            var book = validBook();
            book.setRating(r);

            assertThat(validator.validate(book))
                    .as("rating %d should be valid", r)
                    .isEmpty();
        }
    }

    @Test
    void book_rejectsBlankTitle() {
        var book = new Book();
        book.setTitle("   ");

        assertThat(validator.validate(book))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title");
    }
}
