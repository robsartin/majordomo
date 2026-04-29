package com.majordomo.adapter.in.web.concierge;

/**
 * Form-binding row for one address in the contact add/edit form.
 *
 * <p>Spring's {@code WebDataBinder} requires a no-arg constructor and setters
 * for indexed binding (e.g. {@code addresses[0].street}); a record can't fill
 * that role.</p>
 */
public class AddressFormRow {

    private String label;
    private String street;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * @return true when every field is null or blank — the row should be ignored on save
     */
    public boolean isBlank() {
        return blank(label) && blank(street) && blank(city)
                && blank(state) && blank(postalCode) && blank(country);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
